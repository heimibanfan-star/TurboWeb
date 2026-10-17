package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import top.turboweb.http.testkit.Requests;
import top.turboweb.rest.core.HttpContent;
import top.turboweb.rest.core.HttpRequestMethod;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultMessage} 的组装口径：请求方式快照、地址对象的构造期建立、实例稳定性与
 * 缺省字符集透传，以及平铺在消息上的请求头读取——泛型访问器的大小写不敏感口径、
 * 常用请求头便捷方法的原始值与缺省语义、对请求头集合的实时委托。
 */
class DefaultMessageTest {

    /** 不经头与地址通道的用例统一带入的请求体内容 */
    private static final HttpContent NO_CONTENT = new HttpUnknowContent(null);

    private static DefaultMessage message(FullHttpRequest request, Charset charset) {
        return new DefaultMessage(request, NO_CONTENT, charset);
    }

    // ---------- 组装口径 ----------

    @Test
    void methodIsSnapshottedFromRequestLine() {
        FullHttpRequest get = Requests.get("/x");
        FullHttpRequest post = Requests.postJson("/x", "{}");
        try {
            DefaultMessage getMessage = message(get, StandardCharsets.UTF_8);
            DefaultMessage postMessage = message(post, StandardCharsets.UTF_8);
            assertEquals(HttpRequestMethod.GET, getMessage.method(), "请求行方法字面量转为标准方法");
            assertEquals(HttpRequestMethod.POST, postMessage.method());
            assertSame(getMessage.method(), getMessage.method(), "构造期快照，重复访问同实例");
        } finally {
            Requests.release(get);
            Requests.release(post);
        }
    }

    @Test
    void nonStandardMethodIsRejectedAtConstruction() {
        FullHttpRequest req = Requests.raw(HttpMethod.valueOf("PROPFIND"), "/x", null, null,
                StandardCharsets.UTF_8);
        try {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> message(req, StandardCharsets.UTF_8),
                    "扩展方法不在标准方法范围内，装配期即拒");
            assertTrue(ex.getMessage().contains("PROPFIND"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void contentIsTheInjectedInstance() {
        FullHttpRequest req = Requests.get("/x");
        try {
            HttpContent content = new HttpUnknowContent(null);
            DefaultMessage message = new DefaultMessage(req, content, StandardCharsets.UTF_8);
            assertSame(content, message.content(), "内容由装配方带入，消息不另建");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new DefaultMessage(null, NO_CONTENT, StandardCharsets.UTF_8));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void nullContentIsRejected() {
        FullHttpRequest req = Requests.get("/x");
        try {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new DefaultMessage(req, null, StandardCharsets.UTF_8));
            assertEquals("content can not be null", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void nullQueryCharsetIsRejected() {
        FullHttpRequest req = Requests.get("/x");
        try {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> new DefaultMessage(req, NO_CONTENT, null));
            assertEquals("queryCharset can not be null", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    // ---------- 请求头读取（平铺在消息上） ----------

    @Test
    void headerReturnsFirstValueAndNullWhenMissing() {
        FullHttpRequest req = Requests.get("/x");
        try {
            req.headers().set("Content-Type", "application/json; charset=UTF-8");
            DefaultMessage message = message(req, StandardCharsets.UTF_8);
            assertEquals("application/json; charset=UTF-8", message.header("Content-Type"));
            assertNull(message.header("X-Missing"), "缺头返回 null 而非抛错");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void genericAccessorsIgnoreNameCase() {
        FullHttpRequest req = Requests.get("/x");
        try {
            req.headers().set("X-Custom", "v");
            DefaultMessage message = message(req, StandardCharsets.UTF_8);
            assertEquals("v", message.header("x-custom"), "头名查询大小写不敏感");
            assertTrue(message.containsHeader("X-CUSTOM"));
            assertFalse(message.containsHeader("X-Other"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void headerValuesReturnsValuesInOrderAndEmptyWhenMissing() {
        FullHttpRequest req = Requests.get("/x");
        try {
            req.headers().add("X-Multi", "a");
            req.headers().add("X-Multi", "b");
            DefaultMessage message = message(req, StandardCharsets.UTF_8);
            assertEquals(List.of("a", "b"), message.headerValues("X-Multi"));
            assertEquals("a", message.header("X-Multi"), "单值访问器取首值");
            assertEquals(List.of(), message.headerValues("X-None"), "缺头返回空列表");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void emptyHeadersAndSizeNamesAfterFills() {
        FullHttpRequest req = Requests.get("/x");
        try {
            DefaultMessage message = message(req, StandardCharsets.UTF_8);
            assertTrue(message.headerIsEmpty(), "无头请求记为无头");
            assertEquals(0, message.headerSize());
            assertEquals(Set.of(), message.headerNames());

            req.headers().set("Host", "localhost");
            req.headers().add("X-Multi", "a");
            req.headers().add("X-Multi", "b");
            assertEquals(3, message.headerSize(), "同名多值按条目计数");
            assertTrue(message.headerNames().contains("Host"));
            assertTrue(message.headerNames().contains("X-Multi"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void commonAccessorsReturnRawHeaderValues() {
        FullHttpRequest req = Requests.get("/x");
        try {
            req.headers().set("Content-Type", "application/json; charset=UTF-8");
            req.headers().set("Host", "example.com:8080");
            req.headers().set("Connection", "keep-alive");
            req.headers().set("Date", "Fri, 16 Oct 2026 01:02:03 GMT");
            req.headers().set("Expect", "100-continue");
            req.headers().set("Accept", "text/html");
            req.headers().set("Accept-Charset", "utf-8");
            req.headers().set("Accept-Encoding", "gzip");
            req.headers().set("Accept-Language", "zh-CN");
            req.headers().set("Authorization", "Bearer t");
            req.headers().set("Cache-Control", "no-cache");
            req.headers().set("Cookie", "a=1; b=2");
            req.headers().set("Origin", "https://example.com");
            req.headers().set("Referer", "https://example.com/from");
            req.headers().set("User-Agent", "curl/8");
            req.headers().set("Content-Length", "42");

            DefaultMessage message = message(req, StandardCharsets.UTF_8);
            assertEquals("application/json; charset=UTF-8", message.header("Content-Type"),
                    "便捷方法返回原始头值，编码段等附加参数原样保留");
            assertEquals("example.com:8080", message.host());
            assertEquals("keep-alive", message.connection());
            assertEquals("Fri, 16 Oct 2026 01:02:03 GMT", message.date());
            assertEquals("100-continue", message.expect());
            assertEquals("text/html", message.accept());
            assertEquals("utf-8", message.acceptCharset());
            assertEquals("gzip", message.acceptEncoding());
            assertEquals("zh-CN", message.acceptLanguage());
            assertEquals("Bearer t", message.authorization());
            assertEquals("no-cache", message.cacheControl());
            assertEquals("a=1; b=2", message.cookie());
            assertEquals("https://example.com", message.origin());
            assertEquals("https://example.com/from", message.referer());
            assertEquals("curl/8", message.userAgent());
            assertEquals(42L, message.contentLength());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void commonAccessorsReturnNullWhenAbsent() {
        FullHttpRequest req = Requests.get("/x");
        try {
            DefaultMessage message = message(req, StandardCharsets.UTF_8);
            assertNull(message.header("Content-Type"));
            assertNull(message.host());
            assertNull(message.connection());
            assertNull(message.date());
            assertNull(message.expect());
            assertNull(message.accept());
            assertNull(message.acceptCharset());
            assertNull(message.acceptEncoding());
            assertNull(message.acceptLanguage());
            assertNull(message.authorization());
            assertNull(message.cacheControl());
            assertNull(message.cookie());
            assertNull(message.origin());
            assertNull(message.referer());
            assertNull(message.userAgent());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void contentLengthLenientSemantics() {
        FullHttpRequest req = Requests.get("/x");
        try {
            DefaultMessage message = message(req, StandardCharsets.UTF_8);
            assertEquals(0L, message.contentLength(), "缺头记 0");

            req.headers().set("Content-Length", "abc");
            assertEquals(0L, message.contentLength(), "非数值宽容为 0，与 Netty 缺省口径一致");

            req.headers().set("Content-Length", "-5");
            assertEquals(0L, message.contentLength(), "负值钳为 0");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void headerReadsFollowRequestHeaderMutation() {
        FullHttpRequest req = Requests.get("/x");
        try {
            DefaultMessage message = message(req, StandardCharsets.UTF_8);
            assertFalse(message.containsHeader("X-Late"));
            req.headers().set("X-Late", "v");
            assertEquals("v", message.header("X-Late"), "头读取实时委托请求头集合，不做拷贝快照");
        } finally {
            Requests.release(req);
        }
    }

    // ---------- 请求地址 ----------

    @Test
    void urlExposesUriAndDecodedQueryParams() {
        FullHttpRequest req = Requests.get("/api/search?q=hello&tag=a&tag=b");
        try {
            DefaultMessage message = message(req, StandardCharsets.UTF_8);
            assertEquals("/api/search?q=hello&tag=a&tag=b", message.url().url(false));
            assertEquals("/api/search", message.url().url(true));
            assertEquals(List.of("hello"), message.url().queryParams().get("q"));
            assertEquals(List.of("a", "b"), message.url().queryParams().get("tag"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void queryCharsetPlumbedToUrlDecoding() {
        FullHttpRequest req = Requests.get("/x?n=%D6%D0");
        try {
            DefaultMessage message = message(req, Charset.forName("GBK"));
            assertEquals("中", message.url().queryParams().get("n").get(0),
                    "装配期缺省字符集透传至地址对象的查询解码");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void accessorsReturnStableInstances() {
        FullHttpRequest req = Requests.get("/x");
        try {
            DefaultMessage message = message(req, StandardCharsets.UTF_8);
            assertSame(message.url(), message.url(), "地址对象构造期建立，重复访问同实例");
            assertSame(message.content(), message.content());
        } finally {
            Requests.release(req);
        }
    }

    }
