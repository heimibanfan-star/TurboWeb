package top.turboweb.websocket;

import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * websocket处理器
 */
public interface WebSocketHandler {

    /**
     * 链接建立时调度
     *
     * @param session websocket session回话
     */
    void onOpen(WebSocketSession session);

    /**
     * 处理收到消息
     *
     * @param session websocket session回话
     */
    void onMessage(WebSocketSession session, WebSocketFrame webSocketFrame);

    /**
     * 处理收到关闭消息
     *
     * @param session websocket session回话
     */
    void onClose(WebSocketSession session);
}
