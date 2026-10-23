package top.turboweb.websocket.session;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.net.SocketAddress;

/**
 * WebSocket 会话接口，表示一个客户端与服务器之间的 WebSocket 连接。
 * <p>
 * 提供发送消息、管理连接状态以及获取连接信息的功能。
 * 会话不提供属性挂载入口，连接级业务状态由使用方自行维护。
 * </p>
 */
public interface WebSocketSession {

    /**
     * 异步发送文本消息到客户端。
     *
     * @param message 待发送的文本消息
     * @return {@link ChannelFuture} 用于监听发送结果
     */
    ChannelFuture sendText(String message);

    /**
     * 异步发送二进制数据到客户端。
     *
     * @param message 待发送的二进制消息
     * @return {@link ChannelFuture} 用于监听发送结果
     */
    ChannelFuture sendBinary(byte[] message);

    /**
     * 异步发送二进制数据到客户端。
     *
     * @param byteBuf 待发送的 {@link ByteBuf} 消息对象
     * @return {@link ChannelFuture} 用于监听发送结果
     */
    ChannelFuture sendBinary(ByteBuf byteBuf);

    /**
     * 异步发送任意类型的 WebSocket 帧。
     *
     * @param webSocketFrame 待发送的 {@link WebSocketFrame} 对象
     * @return {@link ChannelFuture} 用于监听发送结果
     */
    ChannelFuture send(WebSocketFrame webSocketFrame);

    /**
     * 获取当前会话的连接路径。
     *
     * @return 当前会话的连接路径
     */
    String path();

    /**
     * 获取当前会话的 HTTP 请求头。
     *
     * @return 当前会话的 HTTP 请求头
     */
    HttpHeaders headers();

    /**
     * 发送 WebSocket Ping 帧，用于心跳检测。
     */
    void sendPing();

    /**
     * 发送 WebSocket Pong 帧，用于响应 Ping 帧。
     */
    void sendPong();

    /**
     * 关闭当前 WebSocket 会话。
     * <p>关闭后，连接将不可用，发送消息将失败。</p>
     */
    void close();

    /**
     * 获取远程客户端的 Socket 地址。
     *
     * @return 远程客户端 {@link SocketAddress}
     */
    SocketAddress remoteAddress();

    /**
     * 获取本地服务端的 Socket 地址。
     *
     * @return 本地服务端 {@link SocketAddress}
     */
    SocketAddress localAddress();
}
