package top.turboweb.rest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link HttpRequestMessage} 的 default 便捷方法：常用头访问器按固定头名落到
 * {@link HttpRequestMessage#header(String)}，Content-Length 取值的兜底口径。
 */
class HttpRequestMessageTest {

    /** 仅头查询可配置的桩实现：其余抽象方法给最小行为，default 方法不触碰它们 */
    private static HttpRequestMessage stub(Function<String, String> headerLookup) {
        return new HttpRequestMessage() {
            @Override
            public HttpRequestMethod method() {
                return HttpRequestMethod.GET;
            }

            @Override
            public HttpUrl url() {
                throw new UnsupportedOperationException();
            }

            @Override
            public HttpContent content() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String header(String name) {
                return headerLookup.apply(name);
            }

            @Override
            public List<String> headerValues(String name) {
                return List.of();
            }

            @Override
            public boolean containsHeader(String name) {
                return false;
            }

            @Override
            public Set<String> headerNames() {
                return Set.of();
            }

            @Override
            public int headerSize() {
                return 0;
            }

            @Override
            public boolean headerIsEmpty() {
                return true;
            }
        };
    }

    /** 按头名回显取值：便捷方法查询的头名写错即断言失配 */
    private static final HttpRequestMessage ECHO = stub(name -> "v-" + name);

    @Test
    void convenienceAccessorsQueryTheirHeaderName() {
        assertEquals("v-Host", ECHO.host());
        assertEquals("v-Connection", ECHO.connection());
        assertEquals("v-Date", ECHO.date());
        assertEquals("v-Expect", ECHO.expect());
        assertEquals("v-Accept", ECHO.accept());
        assertEquals("v-Accept-Charset", ECHO.acceptCharset());
        assertEquals("v-Accept-Encoding", ECHO.acceptEncoding());
        assertEquals("v-Accept-Language", ECHO.acceptLanguage());
        assertEquals("v-Authorization", ECHO.authorization());
        assertEquals("v-Cache-Control", ECHO.cacheControl());
        assertEquals("v-Cookie", ECHO.cookie());
        assertEquals("v-Origin", ECHO.origin());
        assertEquals("v-Referer", ECHO.referer());
        assertEquals("v-User-Agent", ECHO.userAgent());
    }

    @Test
    void convenienceAccessorsReturnNullWhenHeaderAbsent() {
        HttpRequestMessage empty = stub(name -> null);
        assertNull(empty.host());
        assertNull(empty.connection());
        assertNull(empty.date());
        assertNull(empty.expect());
        assertNull(empty.accept());
        assertNull(empty.acceptCharset());
        assertNull(empty.acceptEncoding());
        assertNull(empty.acceptLanguage());
        assertNull(empty.authorization());
        assertNull(empty.cacheControl());
        assertNull(empty.cookie());
        assertNull(empty.origin());
        assertNull(empty.referer());
        assertNull(empty.userAgent());
    }

    @Test
    void contentLengthParsesDeclaredValue() {
        HttpRequestMessage message = stub(name -> " 42 ");
        assertEquals(42L, message.contentLength());
    }

    @Test
    void contentLengthZeroWhenHeaderAbsent() {
        HttpRequestMessage message = stub(name -> null);
        assertEquals(0L, message.contentLength());
    }

    @Test
    void contentLengthZeroWhenValueNotNumeric() {
        HttpRequestMessage message = stub(name -> "abc");
        assertEquals(0L, message.contentLength());
    }

    @Test
    void contentLengthFloorsNegativeValueToZero() {
        HttpRequestMessage message = stub(name -> "-5");
        assertEquals(0L, message.contentLength());
    }
}
