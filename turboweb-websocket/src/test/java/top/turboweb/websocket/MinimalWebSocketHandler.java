package top.turboweb.websocket;

/**
 * 只实现抽象回调的处理器：ping、pong 沿用 {@link WebSocketHandler} 的缺省行为。
 */
final class MinimalWebSocketHandler extends WebSocketHandler {

    @Override
    public void onText(WebSocketSession session, String content) {
    }

    @Override
    public void onBinary(WebSocketSession session, byte[] content) {
    }
}
