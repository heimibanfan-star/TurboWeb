package top.turboweb.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * websocket回话对象
 */
public interface WebSocketSession {

    /**
     * 发送信息
     *
     * @param message 信息
     */
    ChannelFuture sendText(String message);

    /**
     * 发送二进制信息
     *
     * @param message 二进制信息
     */
    ChannelFuture sendBinary(byte[] message);

    /**
     * 发送二进制信息
     *
     * @param byteBuf 二进制信息
     */
    ChannelFuture sendBinary(ByteBuf byteBuf);

    /**
     * 发送信息
     *
     * @param webSocketFrame 信息
     */
    ChannelFuture send(WebSocketFrame webSocketFrame);

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
