package top.turboweb.websocket;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WebSocketHandler} 的裸帧转译：文本帧解码为文本，二进制、ping、pong 帧各拷贝一份字节数组；
 * 缺省 ping 回送 pong，缺省 pong 与开关回调不做处理。
 */
class WebSocketHandlerTest {

    private final RecordingWebSocketSession session = new RecordingWebSocketSession();

    @Test
    void textFrameIsDecodedToText() {
        RecordingWebSocketHandler handler = new RecordingWebSocketHandler();
        TextWebSocketFrame frame = new TextWebSocketFrame("你好 websocket");

        handler.onUnSafeText(session, frame);
        frame.release();

        assertEquals(List.of("text"), handler.events);
        assertEquals("你好 websocket", handler.payloads.get(0));
        assertTrue(session.calls.isEmpty(), "文本回调不应触发会话发送");
    }

    @Test
    void binaryFrameIsCopiedToByteArray() {
        RecordingWebSocketHandler handler = new RecordingWebSocketHandler();
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(Unpooled.copiedBuffer(new byte[]{1, 2, 3}));

        handler.onUnSafeBinary(session, frame);
        // 帧释放后拷出的内容仍然可用：拷贝与帧的生命周期无关
        frame.release();

        assertEquals(List.of("binary"), handler.events);
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) handler.payloads.get(0));
    }

    @Test
    void pingFrameIsCopiedAndDefaultRepliesPong() {
        MinimalWebSocketHandler handler = new MinimalWebSocketHandler();
        PingWebSocketFrame frame = new PingWebSocketFrame(Unpooled.copiedBuffer(new byte[]{9, 8}));

        handler.onUnSafePing(session, frame);
        frame.release();

        assertEquals(List.of("sendPong"), session.calls, "缺省 ping 回调必须回送 pong");
    }

    @Test
    void pongFrameDefaultDoesNothing() {
        MinimalWebSocketHandler handler = new MinimalWebSocketHandler();
        PongWebSocketFrame frame = new PongWebSocketFrame(Unpooled.copiedBuffer(new byte[]{7}));

        handler.onUnSafePong(session, frame);
        frame.release();

        assertTrue(session.calls.isEmpty(), "缺省 pong 回调不应触发任何会话动作");
    }

    @Test
    void overriddenPingAndPongReceiveCopiedBytes() {
        RecordingWebSocketHandler handler = new RecordingWebSocketHandler();
        PingWebSocketFrame ping = new PingWebSocketFrame(Unpooled.copiedBuffer(new byte[]{4, 5}));
        PongWebSocketFrame pong = new PongWebSocketFrame(Unpooled.copiedBuffer(new byte[]{6, 7}));

        handler.onUnSafePing(session, ping);
        handler.onUnSafePong(session, pong);
        ping.release();
        pong.release();

        assertEquals(List.of("ping", "pong"), handler.events);
        assertArrayEquals(new byte[]{4, 5}, (byte[]) handler.payloads.get(0));
        assertArrayEquals(new byte[]{6, 7}, (byte[]) handler.payloads.get(1));
        assertTrue(session.calls.isEmpty(), "覆写后不再走缺省回送 pong");
    }

    @Test
    void openAndCloseDefaultsAreNoOps() {
        MinimalWebSocketHandler handler = new MinimalWebSocketHandler();

        assertDoesNotThrow(() -> handler.onOpen(session));
        assertDoesNotThrow(() -> handler.onClose(session));
        assertTrue(session.calls.isEmpty());
    }
}
