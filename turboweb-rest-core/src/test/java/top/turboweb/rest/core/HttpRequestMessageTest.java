package top.turboweb.rest.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link HttpRequestMessage} 的 default 方法：Content-Length 取值的兜底口径。
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
            public RequestLine requestLine() {
                return new RequestLine(HttpRequestMethod.GET, new HttpRequestTarget("/index?x=1"));
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

    @Test
    void requestLineCarriesMethodAndTarget() {
        HttpRequestMessage message = stub(name -> null);

        RequestLine line = message.requestLine();

        assertEquals(HttpRequestMethod.GET, line.method(), "请求行承载请求方式");
        assertEquals("/index?x=1", line.target().raw(), "请求行承载请求目标");
    }

    @Test
    void headerChannelIsReachableThroughHttpMessageView() {
        HttpMessage<RequestLine, HttpContent> message = stub(name -> " 42 ");

        assertEquals(" 42 ", message.header("Content-Length"), "头读取经 HttpMessage 视图可达");
        assertEquals(42L, message.contentLength(), "Content-Length 兜底口径经 HttpMessage 视图继承");
    }
}
