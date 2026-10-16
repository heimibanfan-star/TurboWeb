package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import org.junit.jupiter.api.Test;
import top.turboweb.http.testkit.Requests;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultHeaders} 的只读头视图：泛型访问器的大小写不敏感口径、常用请求头便捷方法
 * 的原始值与缺省语义、以及对请求头集合的实时委托。
 */
class DefaultHeadersTest {

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new DefaultHeaders(null));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void getReturnsFirstValueAndNullWhenMissing() {
        FullHttpRequest req = Requests.get("/x");
        try {
            req.headers().set("Content-Type", "application/json; charset=UTF-8");
            DefaultHeaders headers = new DefaultHeaders(req);
            assertEquals("application/json; charset=UTF-8", headers.get("Content-Type"));
            assertNull(headers.get("X-Missing"), "缺头返回 null 而非抛错");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void genericAccessorsIgnoreNameCase() {
        FullHttpRequest req = Requests.get("/x");
        try {
            req.headers().set("X-Custom", "v");
            DefaultHeaders headers = new DefaultHeaders(req);
            assertEquals("v", headers.get("x-custom"), "头名查询大小写不敏感");
            assertTrue(headers.contains("X-CUSTOM"));
            assertFalse(headers.contains("X-Other"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void getAllReturnsValuesInOrderAndEmptyWhenMissing() {
        FullHttpRequest req = Requests.get("/x");
        try {
            req.headers().add("X-Multi", "a");
            req.headers().add("X-Multi", "b");
            DefaultHeaders headers = new DefaultHeaders(req);
            assertEquals(List.of("a", "b"), headers.getAll("X-Multi"));
            assertEquals("a", headers.get("X-Multi"), "单值访问器取首值");
            assertEquals(List.of(), headers.getAll("X-None"), "缺头返回空列表");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void emptyViewAndSizeNamesAfterFills() {
        FullHttpRequest req = Requests.get("/x");
        try {
            DefaultHeaders headers = new DefaultHeaders(req);
            assertTrue(headers.isEmpty(), "无头请求视图为空");
            assertEquals(0, headers.size());
            assertEquals(Set.of(), headers.names());

            req.headers().set("Host", "localhost");
            req.headers().add("X-Multi", "a");
            req.headers().add("X-Multi", "b");
            assertEquals(3, headers.size(), "同名多值按条目计数");
            assertTrue(headers.names().contains("Host"));
            assertTrue(headers.names().contains("X-Multi"));
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

            DefaultHeaders headers = new DefaultHeaders(req);
            assertEquals("application/json; charset=UTF-8", headers.contentType(),
                    "便捷方法返回原始头值，编码段等附加参数原样保留");
            assertEquals("example.com:8080", headers.host());
            assertEquals("keep-alive", headers.connection());
            assertEquals("Fri, 16 Oct 2026 01:02:03 GMT", headers.date());
            assertEquals("100-continue", headers.expect());
            assertEquals("text/html", headers.accept());
            assertEquals("utf-8", headers.acceptCharset());
            assertEquals("gzip", headers.acceptEncoding());
            assertEquals("zh-CN", headers.acceptLanguage());
            assertEquals("Bearer t", headers.authorization());
            assertEquals("no-cache", headers.cacheControl());
            assertEquals("a=1; b=2", headers.cookie());
            assertEquals("https://example.com", headers.origin());
            assertEquals("https://example.com/from", headers.referer());
            assertEquals("curl/8", headers.userAgent());
            assertEquals(42L, headers.contentLength());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void commonAccessorsReturnNullWhenAbsent() {
        FullHttpRequest req = Requests.get("/x");
        try {
            DefaultHeaders headers = new DefaultHeaders(req);
            assertNull(headers.contentType());
            assertNull(headers.host());
            assertNull(headers.connection());
            assertNull(headers.date());
            assertNull(headers.expect());
            assertNull(headers.accept());
            assertNull(headers.acceptCharset());
            assertNull(headers.acceptEncoding());
            assertNull(headers.acceptLanguage());
            assertNull(headers.authorization());
            assertNull(headers.cacheControl());
            assertNull(headers.cookie());
            assertNull(headers.origin());
            assertNull(headers.referer());
            assertNull(headers.userAgent());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void contentLengthLenientSemantics() {
        FullHttpRequest req = Requests.get("/x");
        try {
            DefaultHeaders headers = new DefaultHeaders(req);
            assertEquals(0L, headers.contentLength(), "缺头记 0");

            req.headers().set("Content-Length", "abc");
            assertEquals(0L, headers.contentLength(), "非数值宽容为 0，与 Netty 缺省口径一致");

            req.headers().set("Content-Length", "-5");
            assertEquals(0L, headers.contentLength(), "负值钳为 0");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void viewFollowsRequestHeaderMutation() {
        FullHttpRequest req = Requests.get("/x");
        try {
            DefaultHeaders headers = new DefaultHeaders(req);
            assertFalse(headers.contains("X-Late"));
            req.headers().set("X-Late", "v");
            assertEquals("v", headers.get("X-Late"), "视图实时委托请求头集合，不做拷贝快照");
        } finally {
            Requests.release(req);
        }
    }
}
