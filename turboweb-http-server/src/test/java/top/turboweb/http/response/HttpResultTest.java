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
    @DisplayName("HS-23 回归：String 分支写 text/html Content-Type，Content-Length 取实际体长")
    void stringDataIsWrittenAsHtmlWithoutSerialization() {
        StubJsonSerializer serializer = new StubJsonSerializer();
        FullHttpResponse response = (FullHttpResponse) new HttpResult<>("hi <b>").createResponse(serializer);
        try {
            assertEquals(200, response.status().code());
            assertEquals("hi <b>", response.content().toString(GlobalConfig.getResponseCharset()));
            assertEquals(0, serializer.calls.get(), "String 数据不得经过 JSON 序列化");
            assertEquals("text/html;charset=utf-8", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            assertEquals(6, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH),
                    "HS-23 回归：调用方头先整体写入、自算头后写，不再被空 headers 整体 set 抹除");
        } finally {
            response.release();
        }
    }

    @Test
    @DisplayName("HS-23 回归：JSON 分支写 application/json Content-Type，Content-Length 取序列化体长")
    void objectDataIsSerializedAsJson() {
        StubJsonSerializer serializer = new StubJsonSerializer();
        FullHttpResponse response = (FullHttpResponse) new HttpResult<>(Map.of("k", "v")).createResponse(serializer);
        try {
            String json = "{\"ok\":1}";
            assertEquals(json, response.content().toString(GlobalConfig.getResponseCharset()));
            assertEquals(1, serializer.calls.get());
            assertEquals("application/json;charset=utf-8", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            assertEquals(8, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH),
                    "HS-23 回归：Content-Length 按实际序列化体长写入（桩序列化器定值 8 字节），不被调用方空头集抹除");
        } finally {
            response.release();
        }
    }

    @Test
    @DisplayName("HS-23 回归：null 序列化体时 Content-Type 仍在、Content-Length=0")
    void nullSerializedContentProducesZeroLengthBody() {
        StubJsonSerializer serializer = new StubJsonSerializer();
        serializer.json = null; // 序列化器显式返回 null，走 setContent 的 content==null 分支
        FullHttpResponse response = (FullHttpResponse) new HttpResult<>(new Object()).createResponse(serializer);
        try {
            assertEquals(0, response.content().readableBytes());
            assertEquals("application/json;charset=utf-8", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            assertEquals(0, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH),
                    "HS-23 回归：0 长度的 Content-Length 同样是显式写入的头，不被抹除");
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
