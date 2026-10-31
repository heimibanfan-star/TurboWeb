package top.turboweb.http.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ByteResponseMessage} 的纯持有语义：字节数组响应体由调用方产出，
 * 消息只按入参原样持有状态行与数组本体。
 */
class ByteResponseMessageTest {

    @Test
    void nullStatusLineIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new ByteResponseMessage(null, new byte[0]));
        assertSame("line must not be null", ex.getMessage());
    }

    @Test
    void nullContentIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new ByteResponseMessage(StatusLine.of(200), null));
        assertSame("content must not be null", ex.getMessage());
    }

    @Test
    void messageCarriesInjectedStatusLineAndBytes() {
        StatusLine statusLine = StatusLine.of(200);
        byte[] content = {1, 2, 3};
        ByteResponseMessage message = new ByteResponseMessage(statusLine, content);

        assertSame(statusLine, message.statusLine(), "状态行按入参原样持有");
        assertSame(content, message.content(), "字节数组按入参原样持有，不作复制");
        assertArrayEquals(new byte[]{1, 2, 3}, message.content());
    }

    @Test
    void emptyArrayIsHeldVerbatim() {
        ByteResponseMessage message = new ByteResponseMessage(StatusLine.of(200), new byte[0]);

        assertArrayEquals(new byte[0], message.content());
    }
}
