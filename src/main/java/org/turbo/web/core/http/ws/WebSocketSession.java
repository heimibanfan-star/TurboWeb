package org.turbo.web.core.http.ws;

import io.netty.channel.ChannelFuture;

/**
 * websocket回话对象
 */
public interface WebSocketSession {

    /**
     * 发送信息
     *
     * @param message 信息
     */
    ChannelFuture sendMessage(String message);

    /**
     * 获取连接信息
     *
     * @return 连接信息
     */
    WebSocketConnectInfo getWebSocketConnectInfo();

    /**
     * 发送ping
     *
     */
    void sendPing();

    /**
     * 发送pong
     *
     */
    void sendPong();

    /**
     * 关闭连接
     *
     */
    void close();
}
