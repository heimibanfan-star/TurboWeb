package top.turboweb.http.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ByteRequestMessage} 的纯持有语义：字节数组请求体由解析器或调用方产出，
 * 消息只按入参原样持有请求行与数组本体。
 */
class ByteRequestMessageTest {

    @Test
    void nullLineIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new ByteRequestMessage(null, new byte[0]));
        assertSame("line must not be null", ex.getMessage());
    }

    @Test
    void nullContentIsRejected() {
        RequestLine line = new RequestLine(HttpRequestMethod.POST, new RequestTarget("/x"));
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new ByteRequestMessage(line, null));
        assertSame("content must not be null", ex.getMessage());
    }

    @Test
    void messageCarriesInjectedLineAndBytes() {
        RequestLine line = new RequestLine(HttpRequestMethod.POST, new RequestTarget("/x"));
        byte[] content = {1, 2, 3};
        ByteRequestMessage message = new ByteRequestMessage(line, content);

        assertSame(line, message.requestLine(), "请求行按入参原样持有");
        assertSame(content, message.content(), "字节数组按入参原样持有，不作复制");
        assertArrayEquals(new byte[]{1, 2, 3}, message.content());
    }

    @Test
    void emptyArrayIsHeldVerbatim() {
        RequestLine line = new RequestLine(HttpRequestMethod.POST, new RequestTarget("/x"));
        ByteRequestMessage message = new ByteRequestMessage(line, new byte[0]);

        assertArrayEquals(new byte[0], message.content());
    }
}
