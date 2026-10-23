package top.turboweb.websocket;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录全部业务回调的处理器替身；四个帧回调均覆写，用于核对分发结果。
 */
final class RecordingWebSocketHandler extends WebSocketHandler {

    /** 按调用顺序记录的回调名 */
    final List<String> events = new ArrayList<>();

    /** 按调用顺序记录的回调载荷：文本、字节数组或空 */
    final List<Object> payloads = new ArrayList<>();

    /** 最近一次回调收到的会话 */
    WebSocketSession lastSession;

    /** 置为 true 时文本回调抛出异常，用于核对异常传播与帧归还 */
    boolean failOnText;

    @Override
    public void onOpen(WebSocketSession session) {
        events.add("open");
        lastSession = session;
    }

    @Override
    public void onText(WebSocketSession session, String content) {
        if (failOnText) {
            throw new IllegalStateException("boom");
        }
        events.add("text");
        payloads.add(content);
        lastSession = session;
    }

    @Override
    public void onBinary(WebSocketSession session, byte[] content) {
        events.add("binary");
        payloads.add(content);
        lastSession = session;
    }

    @Override
    public void onPing(WebSocketSession session, byte[] content) {
        events.add("ping");
        payloads.add(content);
        lastSession = session;
    }

    @Override
    public void onPong(WebSocketSession session, byte[] content) {
        events.add("pong");
        payloads.add(content);
        lastSession = session;
    }

    @Override
    public void onClose(WebSocketSession session) {
        events.add("close");
        lastSession = session;
    }
}
