package top.turboweb.websocket.session;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import top.turboweb.websocket.info.WebSocketConnectInfo;

import java.net.SocketAddress;

/**
 * 标准的 WebSocket 会话实现，封装 {@link Channel} 并提供 WebSocket 消息发送、
 * 会话属性管理和连接信息访问等功能。
 * <p>
 * 该类实现 {@link WebSocketSession} 接口，适用于基于 Netty 的 WebSocket 服务器端。
 * 提供对文本、二进制及自定义 WebSocket 帧的异步发送能力，并支持心跳帧的发送。
 * </p>
 */
public class StandardWebSocketSession implements WebSocketSession{

    /** Netty 通道，用于发送和接收 WebSocket 消息 */
    private final Channel channel;

    /** WebSocket 连接信息 */
    private final WebSocketConnectInfo webSocketConnectInfo;

    /**
     * 构造方法，初始化 WebSocket 会话。
     *
     * @param channel 关联的 Netty {@link Channel}
     * @param connectInfo 当前会话的 {@link WebSocketConnectInfo} 元数据
     */
    public StandardWebSocketSession(Channel channel, WebSocketConnectInfo connectInfo) {
        this.channel = channel;
        this.webSocketConnectInfo = connectInfo;
    }

    @Override
    public WebSocketConnectInfo connectInfo() {
        return this.webSocketConnectInfo;
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
     * 向当前会话绑定一个自定义属性。
     *
     * @param key 属性键
     * @param value 属性值
     */
    @Override
    public void putAttr(String key, Object value) {
        AttributeKey<Object> attributeKey = AttributeKey.valueOf(key);
        channel.attr(attributeKey).set(value);
    }

    /**
     * 如果当前属性值等于指定的旧值，则更新为新值。
     *
     * @param key 属性键
     * @param oldValue 期望的旧值
     * @param newValue 新值
     * @return 更新是否成功
     */
    @Override
    public boolean compareAndPutAttr(String key, Object oldValue, Object newValue) {
        AttributeKey<Object> attributeKey = AttributeKey.valueOf(key);
        return channel.attr(attributeKey).compareAndSet(oldValue, newValue);
    }

    /**
     * 当指定属性不存在时设置新值，若属性已存在，则返回旧值且不修改。
     *
     * @param key 属性键
     * @param value 属性值
     * @return 若属性已存在返回旧值，否则返回 {@code null}
     */
    @Override
    public Object putAttrIfAbsent(String key, Object value) {
        AttributeKey<Object> attributeKey = AttributeKey.valueOf(key);
        return channel.attr(attributeKey).setIfAbsent(value);
    }

    /**
     * 获取指定键的属性值。
     *
     * @param key 属性键
     * @return 属性值，如果不存在则返回 {@code null}
     */
    @Override
    public Object getAttr(String key) {
        AttributeKey<Object> attributeKey = AttributeKey.valueOf(key);
        return channel.attr(attributeKey).get();
    }

    /**
     * 获取指定键的属性值，并转换为指定类型。
     *
     * @param key 属性键
     * @param type 属性值类型
     * @param <T> 属性值泛型
     * @return 属性值，如果不存在或类型不匹配返回 {@code null}
     */
    @Override
    public <T> T getAttr(String key, Class<T> type) {
        AttributeKey<Object> attributeKey = AttributeKey.valueOf(key);
        Object object = channel.attr(attributeKey).get();
        if (object == null) {
            return null;
        }
        return type.cast(object);
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
}
