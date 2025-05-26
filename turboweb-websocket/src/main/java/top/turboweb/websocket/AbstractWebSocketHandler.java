package top.turboweb.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.*;

/**
 * 简单的websocket处理器抽象类
 */
public abstract class AbstractWebSocketHandler implements WebSocketHandler{

    @Override
    public void onOpen(WebSocketSession session) {}

    @Override
    public void onMessage(WebSocketSession session, WebSocketFrame webSocketFrame) {
        try {
            if (webSocketFrame instanceof TextWebSocketFrame textWebSocketFrame) {
                onText(session, textWebSocketFrame.text());
            } else if (webSocketFrame instanceof BinaryWebSocketFrame binaryWebSocketFrame) {
                onBinary(session, binaryWebSocketFrame.content());
            } else if (webSocketFrame instanceof PingWebSocketFrame) {
                onPing(session);
            } else if (webSocketFrame instanceof PongWebSocketFrame) {
                onPong(session);
            }
        } finally {
            if (webSocketFrame.refCnt() > 0) {
                webSocketFrame.release();
            }
        }
    }

    @Override
    public void onClose(WebSocketSession session) {}

    /**
     * 处理收到文本消息
     *
     * @param session websocket session回话
     * @param content 消息内容
     */
    public abstract void onText(WebSocketSession session, String content);

    /**
     * 处理收到二进制消息
     *
     * @param session websocket session回话
     * @param content 消息内容
     */
    public abstract void onBinary(WebSocketSession session, ByteBuf content);

    /**
     * 处理收到ping消息
     *
     * @param session websocket session回话
     */
    public void onPing(WebSocketSession session) {
        session.sendPong();
    }

    /**
     * 处理收到pong消息
     *
     * @param session websocket session回话
     */
    public void onPong(WebSocketSession session) {
    }
}
