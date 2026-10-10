package top.turboweb.http.core.response;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpUtil;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.exception.TurboSseException;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.testkit.RecordingConnectSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SseEmitter} 的缓存/直发状态机与头部设置（纯逻辑，无通道）。
 *
 * <p>{@code isInit}/{@code messageCache}/{@code cacheLock}/{@code sseLock} 是 protected，
 * 测试与被测类同包，直接读字段即为断言对象。
 */
class SseEmitterTest {

    /** SseEmitter 无抽象方法，探针子类只为拿到一个可实例化的具体类型。 */
    private static final class ProbeEmitter extends SseEmitter {
        ProbeEmitter(ConnectSession session, int maxMessageCache) {
            super(session, maxMessageCache);
        }

        int cachedCount() {
            return messageCache.size();
        }

        String cachedAt(int index) {
            return messageCache.get(index);
        }
    }

    @Test
    void constructorSetsSseHeaders() {
        ProbeEmitter emitter = new ProbeEmitter(new RecordingConnectSession(), 10);

        assertEquals("text/event-stream", emitter.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals("no-cache", emitter.headers().get(HttpHeaderNames.CACHE_CONTROL));
        assertEquals("keep-alive", emitter.headers().get(HttpHeaderNames.CONNECTION));
        assertEquals("chunked", emitter.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
        assertTrue(HttpUtil.isTransferEncodingChunked(emitter));
        assertFalse(emitter.isInit, "刚构造的发射器尚未初始化");
        assertEquals(0, emitter.cachedCount());
    }

    @Test
    void messagesBeforeInitAreCachedAndFormatted() {
        RecordingConnectSession session = new RecordingConnectSession();
        ProbeEmitter emitter = new ProbeEmitter(session, 10);

        emitter.send("plain");
        emitter.sendData("d");
        emitter.sendEvent("e");
        emitter.sendId("i");
        emitter.sendRetry(5);
        emitter.sendComment("c");

        assertEquals(List.of("plain", "data:d\n\n", "event:e\n\n", "id:i\n\n", "retry:5\n\n", ":c\n\n"),
                List.of(emitter.cachedAt(0), emitter.cachedAt(1), emitter.cachedAt(2),
                        emitter.cachedAt(3), emitter.cachedAt(4), emitter.cachedAt(5)));
        assertTrue(session.sentMessages().isEmpty(), "未初始化不得向会话写出，只能进缓存");
    }

    @Test
    void messagesAfterInitAreSentDirectly() {
        RecordingConnectSession session = new RecordingConnectSession();
        ProbeEmitter emitter = new ProbeEmitter(session, 10);
        emitter.isInit = true;

        emitter.sendData("x");

        assertEquals(List.of("data:x\n\n"), session.sentMessages());
        assertEquals(0, emitter.cachedCount(), "已初始化时消息不得再进缓存");
    }

    @Test
    void cacheOverflowAtCapacityThrows() {
        ProbeEmitter emitter = new ProbeEmitter(new RecordingConnectSession(), 2);
        emitter.send("a");
        emitter.send("b");

        TurboSseException ex = assertThrows(TurboSseException.class, () -> emitter.send("c"));
        assertEquals("消息缓存已满", ex.getMessage());
        assertEquals(2, emitter.cachedCount(), "被拒绝的消息不得进入缓存");
    }

    @Test
    void zeroCapacityRejectsFirstMessage() {
        ProbeEmitter emitter = new ProbeEmitter(new RecordingConnectSession(), 0);
        assertThrows(TurboSseException.class, () -> emitter.send("a"));
    }

    @Test
    void closeDelegatesToSession() {
        RecordingConnectSession session = new RecordingConnectSession();
        ProbeEmitter emitter = new ProbeEmitter(session, 10);

        emitter.close();

        assertEquals(1, session.closeCount());
    }

    @Test
    void onCloseCallbackFiresOnSessionClose() {
        RecordingConnectSession session = new RecordingConnectSession();
        ProbeEmitter emitter = new ProbeEmitter(session, 10);
        SseEmitter[] captured = new SseEmitter[1];

        emitter.onClose(e -> captured[0] = e);
        session.fireCloseListeners();

        assertSame(emitter, captured[0], "连接关闭时回调必须收到 emitter 自身");
    }

    @Test
    void getTypeIsSse() {
        ProbeEmitter emitter = new ProbeEmitter(new RecordingConnectSession(), 10);
        assertEquals(InternalCallResponse.InternalCallType.SSE, emitter.getType());
    }
}
