package top.turboweb.rest.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link HttpRequestMessage} 的持有契约：构造期持有请求行、地址与请求体内容，
 * 请求方式由请求行承接，头部读写由 {@link AbstractHttpMessage} 承载。
 */
class HttpRequestMessageTest {

    /** 不经地址通道的用例统一带入的地址替身 */
    private static final HttpUrl URL = new HttpUrl() {
        @Override
        public String url(boolean pathOnly) {
            return "/index?x=1";
        }

        @Override
        public Map<String, List<String>> queryParams() {
            return Map.of();
        }
    };

    /** 不经内容通道的用例统一带入的未声明类型内容 */
    private static final HttpContent NO_CONTENT = () -> null;

    private static HttpRequestMessage message() {
        return new HttpRequestMessage(
                new RequestLine(HttpRequestMethod.GET, new HttpRequestTarget("/index?x=1")),
                URL, NO_CONTENT);
    }

    @Test
    void carriesConstructorInjectedState() {
        RequestLine line = new RequestLine(HttpRequestMethod.POST, new HttpRequestTarget("/x"));
        HttpRequestMessage message = new HttpRequestMessage(line, URL, NO_CONTENT);

        assertEquals(HttpRequestMethod.POST, message.method(), "请求方式由请求行承接");
        assertSame(line, message.requestLine(), "请求行按入参原样持有");
        assertSame(URL, message.url(), "地址按入参原样持有");
        assertSame(NO_CONTENT, message.content(), "内容按入参原样持有");
    }

    @Test
    void headerChannelInheritedFromAbstractHttpMessage() {
        HttpRequestMessage message = message();
        message.setHeader("Content-Type", "application/json");
        message.addHeader("X-Multi", "a");
        message.addHeader("X-Multi", "b");

        assertEquals("application/json", message.header("content-type"),
                "头读写在消息上直接可用，头名查询大小写不敏感");
        assertEquals(List.of("a", "b"), message.headerValues("X-Multi"));
    }

    @Test
    void headerChannelIsReachableThroughHttpMessageView() {
        HttpRequestMessage message = message();
        message.setHeader("Content-Length", " 42 ");

        HttpMessage<RequestLine, HttpContent> view = message;

        assertEquals(" 42 ", view.header("Content-Length"), "头读取经 HttpMessage 视图可达");
        assertEquals(42L, view.contentLength(), "Content-Length 兜底口径经 HttpMessage 视图继承");
    }

    @Test
    void nullRequestLineIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new HttpRequestMessage(null, URL, NO_CONTENT));

        assertEquals("requestLine must not be null", e.getMessage());
    }

    @Test
    void nullUrlIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new HttpRequestMessage(
                        new RequestLine(HttpRequestMethod.GET, new HttpRequestTarget("/x")), null, NO_CONTENT));

        assertEquals("url must not be null", e.getMessage());
    }

    @Test
    void nullContentIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new HttpRequestMessage(
                        new RequestLine(HttpRequestMethod.GET, new HttpRequestTarget("/x")), URL, null));

        assertEquals("content must not be null", e.getMessage());
    }
}
