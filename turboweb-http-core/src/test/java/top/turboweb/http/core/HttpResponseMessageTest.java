package top.turboweb.http.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link HttpResponseMessage} 的持有契约：构造期持有状态行与响应体内容，
 * 响应状态由状态行承接，头部读写由 {@link AbstractHttpMessage} 承载；
 * 无体消息 {@link VoidResponseMessage} 的内容恒为 {@code null}。
 */
class HttpResponseMessageTest {

    /** 不经内容通道的用例统一带入的标记内容 */
    private static final byte[] NO_CONTENT = new byte[0];

    @Test
    void carriesConstructorInjectedState() {
        StatusLine statusLine = StatusLine.of(404);
        HttpResponseMessage<byte[]> message = new HttpResponseMessage<>(statusLine, NO_CONTENT);

        assertEquals(404, message.statusLine().code(), "响应状态由状态行承接");
        assertSame(statusLine, message.statusLine(), "状态行按入参原样持有");
        assertSame(NO_CONTENT, message.content(), "内容按入参原样持有");
    }

    @Test
    void headerChannelInheritedFromAbstractHttpMessage() {
        HttpResponseMessage<byte[]> message = new HttpResponseMessage<>(StatusLine.of(200), NO_CONTENT);
        message.setHeader("Content-Type", "application/json");

        assertEquals("application/json", message.header("content-type"),
                "头读写在消息上直接可用，头名查询大小写不敏感");
    }

    @Test
    void headerChannelIsReachableThroughHttpMessageView() {
        HttpResponseMessage<byte[]> message = new HttpResponseMessage<>(StatusLine.of(200), NO_CONTENT);
        message.setHeader("Content-Length", " 42 ");

        HttpMessage<StatusLine, byte[]> view = message;

        assertEquals(" 42 ", view.header("Content-Length"), "头读取经 HttpMessage 视图可达");
        assertEquals(42L, view.contentLength(), "Content-Length 兜底口径经 HttpMessage 视图继承");
    }

    @Test
    void nullStatusLineIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new HttpResponseMessage<>(null, NO_CONTENT));

        assertEquals("line must not be null", e.getMessage());
    }

    @Test
    void nullContentIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new HttpResponseMessage<>(StatusLine.of(200), null));

        assertEquals("content must not be null", e.getMessage());
    }

    @Test
    void voidMessageCarriesStatusLineWithoutContent() {
        StatusLine statusLine = StatusLine.of(204);
        VoidResponseMessage message = new VoidResponseMessage(statusLine);

        assertSame(statusLine, message.statusLine(), "无体消息状态行按入参原样持有");
        assertNull(message.content(), "无体消息内容恒为 null");
        assertEquals(204, message.statusLine().code(), "响应状态由状态行承接");
    }

    @Test
    void voidMessageNullStatusLineIsRejected() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new VoidResponseMessage(null));

        assertEquals("line must not be null", e.getMessage());
    }
}
