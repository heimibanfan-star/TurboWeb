package top.turboweb.http.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link RequestLine} 的存储契约：请求方式与请求目标按构造入参原样持有，缺一即拒。
 */
class RequestLineTest {

    @Test
    void carriesMethodAndTargetAsGiven() {
        RequestTarget given = new RequestTarget("/upload?name=a");

        RequestLine line = new RequestLine(HttpRequestMethod.POST, given);

        assertSame(HttpRequestMethod.POST, line.method(), "请求方式按入参原样持有");
        assertSame(given, line.target(), "请求目标按入参原样持有");
    }

    @Test
    void nullMethodIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new RequestLine(null, new RequestTarget("/x")));

        assertEquals("method must not be null", e.getMessage());
    }

    @Test
    void nullTargetIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new RequestLine(HttpRequestMethod.GET, null));

        assertEquals("target must not be null", e.getMessage());
    }
}
