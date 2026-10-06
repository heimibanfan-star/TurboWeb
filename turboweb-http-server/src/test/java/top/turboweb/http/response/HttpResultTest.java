package top.turboweb.http.response;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.serializer.JsonSerializer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link HttpResult} 的状态码/头/体承载、String 与 JSON 两条内容分支（spec §5：response）。
 *
 * <p>{@code createResponse} 只接受 {@link JsonSerializer}，这里用只实现 {@code beanToJson} 的
 * 记录型替身，真值来源是序列化器返回值，避免引入 Mockito/AssertJ（spec §2.2）。
 */
class HttpResultTest {

    private static final class StubJsonSerializer implements JsonSerializer {
        private final AtomicInteger calls = new AtomicInteger();
        private String json = "{\"ok\":1}";

        @Override
        public String beanToJson(Object bean) {
            calls.incrementAndGet();
            return json;
        }

        @Override public <T> T mapToBean(Map<String, Object> map, Class<T> beanClass) {
            throw new UnsupportedOperationException();
        }
        @Override public Map<?, ?> beanToMap(Object bean) {
            throw new UnsupportedOperationException();
        }
        @Override public <T> T jsonToBean(String json, Class<T> beanClass) {
            throw new UnsupportedOperationException();
        }
        @Override public Map<?, ?> jsonToMap(String json) {
            throw new UnsupportedOperationException();
        }
        @Override public <T> List<T> jsonToList(String json, Class<T> beanClass) {
            throw new UnsupportedOperationException();
        }
        @Override public <T> T deepinClone(T bean) {
            throw new UnsupportedOperationException();
        }
        @Override public <T> T jsonUpdateBean(String json, T bean) {
            throw new UnsupportedOperationException();
        }
    }

    /** 状态码矩阵用：不关心序列化结果，只需要一个不抛的替身。 */
    private static final StubJsonSerializer SHARED = new StubJsonSerializer();

    private static int statusOf(HttpResult<?> result) {
        FullHttpResponse response = (FullHttpResponse) result.createResponse(SHARED);
        try {
            return response.status().code();
        } finally {
            response.release();
        }
    }

    @Test
    @DisplayName("钉住 HS-23：String 分支 :53 的 Content-Type 与 :76 的 Content-Length 被 :62 空 headers 整体 set 抹除")
    void stringDataIsWrittenAsHtmlWithoutSerialization() {
        StubJsonSerializer serializer = new StubJsonSerializer();
        FullHttpResponse response = (FullHttpResponse) new HttpResult<>("hi <b>").createResponse(serializer);
        try {
            assertEquals(200, response.status().code());
            assertEquals("hi <b>", response.content().toString(GlobalConfig.getResponseCharset()));
            assertEquals(0, serializer.calls.get(), "String 数据不得经过 JSON 序列化");
            // HttpResult.java:42 两参构造恒建空 DefaultHttpHeaders，:62 无条件 response.headers().set(headers)
            // （Netty 4.2 DefaultHeaders.set(Headers) 语义为 clear+add），:53 的 Content-Type 与 :76 的 Content-Length
            // 都被清空；状态码与响应体不依赖头，仍逐字断言。
            // 钉住 HS-23：若将来修正，本用例应改为期望正确行为（Content-Type=text/html;charset=utf-8、Content-Length=6）
            assertNull(response.headers().get(HttpHeaderNames.CONTENT_TYPE),
                    "HS-23：空 headers 的整体 set 清空了 Content-Type，钉住现状");
            assertNull(response.headers().get(HttpHeaderNames.CONTENT_LENGTH),
                    "HS-23：同上，Content-Length 也被清空，钉住现状");
        } finally {
            response.release();
        }
    }

    @Test
    @DisplayName("钉住 HS-23：JSON 分支 :57 的 Content-Type 与 :76 的 Content-Length 被 :62 空 headers 整体 set 抹除")
    void objectDataIsSerializedAsJson() {
        StubJsonSerializer serializer = new StubJsonSerializer();
        FullHttpResponse response = (FullHttpResponse) new HttpResult<>(Map.of("k", "v")).createResponse(serializer);
        try {
            String json = "{\"ok\":1}";
            assertEquals(json, response.content().toString(GlobalConfig.getResponseCharset()));
            assertEquals(1, serializer.calls.get());
            // 同上：:42 空 headers + :62 无条件整体 set（clear+add），:57 与 :76 写下的两头必被抹为 null。
            // 钉住 HS-23：若将来修正，本用例应改为期望正确行为（Content-Type=application/json;charset=utf-8、Content-Length=9）
            assertNull(response.headers().get(HttpHeaderNames.CONTENT_TYPE),
                    "HS-23：空 headers 的整体 set 清空了 Content-Type，钉住现状");
            assertNull(response.headers().get(HttpHeaderNames.CONTENT_LENGTH),
                    "HS-23：同上，Content-Length 也被清空，钉住现状");
        } finally {
            response.release();
        }
    }

    @Test
    @DisplayName("钉住 HS-23：null 序列化体时 :57 的 Content-Type 与 :78 的 Content-Length=0 被 :62 空 headers 整体 set 抹除")
    void nullSerializedContentProducesZeroLengthBody() {
        StubJsonSerializer serializer = new StubJsonSerializer();
        serializer.json = null; // 序列化器显式返回 null，走 setContent 的 content==null 分支
        FullHttpResponse response = (FullHttpResponse) new HttpResult<>(new Object()).createResponse(serializer);
        try {
            assertEquals(0, response.content().readableBytes());
            // 同上：:42 空 headers + :62 无条件整体 set（clear+add），:57 与 :78 写下的两头必被抹为 null；
            // 空响应体不是头，仍逐字钉 0 长度。
            // 钉住 HS-23：若将来修正，本用例应改为期望正确行为（Content-Type=application/json;charset=utf-8、Content-Length=0）
            assertNull(response.headers().get(HttpHeaderNames.CONTENT_TYPE),
                    "HS-23：空 headers 的整体 set 清空了 Content-Type，钉住现状");
            assertNull(response.headers().get(HttpHeaderNames.CONTENT_LENGTH),
                    "HS-23：同上，Content-Length（含 0 值）也被清空，钉住现状");
        } finally {
            response.release();
        }
    }

    @Test
    void customHeadersAreAppliedToTheResponse() {
        HttpHeaders headers = new DefaultHttpHeaders().add("X-Trace-Id", "t-1");
        FullHttpResponse response =
                (FullHttpResponse) HttpResult.create(201, headers, "created").createResponse(SHARED);
        try {
            assertEquals(201, response.status().code());
            assertEquals("t-1", response.headers().get("X-Trace-Id"));
        } finally {
            response.release();
        }
    }

    @Test
    void factoryMethodsCarryExpectedStatusCodes() {
        assertEquals(200, statusOf(HttpResult.ok()));
        assertEquals(200, statusOf(HttpResult.ok("body")));
        assertEquals(500, statusOf(HttpResult.err()));
        assertEquals(500, statusOf(HttpResult.err("boom")));
        assertEquals(201, statusOf(HttpResult.create(201, "created")));
        assertEquals(404, statusOf(new HttpResult<Void>(404)));
        assertEquals(202, statusOf(new HttpResult<>(202, "accepted")));
    }
}
