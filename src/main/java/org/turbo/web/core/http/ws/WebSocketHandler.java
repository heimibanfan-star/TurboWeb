package org.turbo.web.core.http.ws;

import java.util.Map;

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
    void onMessage(WebSocketSession session, String message);

    /**
     * 处理收到关闭消息
     *
     * @param session websocket session回话
     */
    void onClose(WebSocketSession session);

    /**
     * 处理收到ping消息
     *
     * @param session websocket session回话
     */
    default void onPing(WebSocketSession session) {
    }

    /**
     * 处理收到pong消息
     *
     * @param session websocket session回话
     */
    default void onPong(WebSocketSession session) {
    }

    /**
     * 处理回话丢失的问题
     *
     * @param newSession 新创建的回话
     * @param sessionMap session回话的map
     * @param channelId 管道id
     */
    default void onSessionMiss(WebSocketSession newSession, Map<String, WebSocketSession> sessionMap, String channelId) {

    }
}
