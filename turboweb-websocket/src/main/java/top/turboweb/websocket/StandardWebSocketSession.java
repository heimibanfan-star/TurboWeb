package top.turboweb.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.*;

import java.net.SocketAddress;

/**
 * 标准的 WebSocket 会话实现，封装 {@link Channel} 并提供 WebSocket 消息发送、
 * 连接信息访问等功能，不承载使用方自定义属性。
 * <p>
 * 该类实现 {@link WebSocketSession} 接口，适用于基于 Netty 的 WebSocket 服务器端。
 * 提供对文本、二进制及自定义 WebSocket 帧的异步发送能力，并支持心跳帧的发送。
 * </p>
 */
public class StandardWebSocketSession implements WebSocketSession{

    /** Netty 通道，用于发送和接收 WebSocket 消息 */
    private final Channel channel;

    /** 握手请求的uri路径 */
    private final String path;

    /** 握手请求的请求头 */
    private final HttpHeaders headers;

    /**
     * 构造方法，初始化 WebSocket 会话。
     *
     * @param channel 关联的 Netty {@link Channel}
     * @param path    当前会话握手请求的uri路径
     * @param headers 当前会话握手请求的请求头
     */
    public StandardWebSocketSession(Channel channel, String path, HttpHeaders headers) {
        this.channel = channel;
        this.path = path;
        this.headers = headers;
    }

    /**
     * 发送 WebSocket Ping 帧，用于心跳检测。
     */
    @Override
    public void sendPing() {
        PingWebSocketFrame pingWebSocketFrame = new PingWebSocketFrame(Unpooled.EMPTY_BUFFER);
        channel.writeAndFlush(pingWebSocketFrame);
    }

    /**
     * 发送 WebSocket Pong 帧，用于响应 Ping 帧。
     */
    @Override
    public void sendPong() {
        PongWebSocketFrame pongWebSocketFrame = new PongWebSocketFrame(Unpooled.EMPTY_BUFFER);
        channel.writeAndFlush(pongWebSocketFrame);
    }

    /**
     * 关闭当前 WebSocket 会话及底层 Netty 通道。
     */
    @Override
    public void close() {
        channel.close();
    }

    /**
     * 获取远程客户端的 Socket 地址。
     *
     * @return {@link SocketAddress} 表示远程客户端地址
     */
    @Override
    public SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    /**
     * 获取本地服务端的 Socket 地址。
     *
     * @return {@link SocketAddress} 表示本地服务端地址
     */
    @Override
    public SocketAddress localAddress() {
        return channel.localAddress();
    }

    /**
     * 异步发送文本消息到客户端。
     *
     * @param message 待发送的文本消息
     * @return {@link ChannelFuture} 可用于监听发送结果
     */
    @Override
    public ChannelFuture sendText(String message) {
        TextWebSocketFrame textWebSocketFrame = new TextWebSocketFrame(message);
        return channel.writeAndFlush(textWebSocketFrame);
    }

    /**
     * 异步发送二进制消息到客户端。
     *
     * @param message 待发送的字节数组
     * @return {@link ChannelFuture} 可用于监听发送结果
     */
    @Override
    public ChannelFuture sendBinary(byte[] message) {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(message);
        return sendBinary(byteBuf);
    }

    /**
     * 异步发送二进制消息到客户端。
     *
     * @param byteBuf 待发送的 {@link ByteBuf} 对象
     * @return {@link ChannelFuture} 可用于监听发送结果
     */
    @Override
    public ChannelFuture sendBinary(ByteBuf byteBuf) {
        BinaryWebSocketFrame binaryWebSocketFrame = new BinaryWebSocketFrame(byteBuf);
        return channel.writeAndFlush(binaryWebSocketFrame);
    }

    /**
     * 异步发送任意类型的 WebSocket 帧。
     *
     * @param webSocketFrame 待发送的 {@link WebSocketFrame} 对象
     * @return {@link ChannelFuture} 可用于监听发送结果
     */
    @Override
    public ChannelFuture send(WebSocketFrame webSocketFrame) {
        return channel.writeAndFlush(webSocketFrame);
    }

    /**
     * 获取当前会话的连接路径。
     *
     * @return 当前会话握手请求的uri路径
     */
    @Override
    public String path() {
        return path;
    }

    /**
     * 获取当前会话的请求头信息。
     *
     * @return {@link HttpHeaders} 当前会话的请求头信息
     */
    @Override
    public HttpHeaders headers() {
        return headers;
    }
}
