package org.turbo.web.core.http.ws;

/**
 * websocket回话对象
 */
public interface WebSocketSession {

    /**
     * 发送信息
     *
     * @param message 信息
     */
    void sendMessage(String message);
}
