package org.turboweb.core.http.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.*;

/**
 * 封装websocket的回话对象
 */
public class StandardWebSocketSession implements WebSocketSession{

    private final Channel channel;
    private final WebSocketConnectInfo webSocketConnectInfo;

    public StandardWebSocketSession(Channel channel, WebSocketConnectInfo connectInfo) {
        this.channel = channel;
        this.webSocketConnectInfo = connectInfo;
    }

    @Override
    public WebSocketConnectInfo getWebSocketConnectInfo() {
        return this.webSocketConnectInfo;
    }

    @Override
    public void sendPing() {
        PingWebSocketFrame pingWebSocketFrame = new PingWebSocketFrame(Unpooled.EMPTY_BUFFER);
        channel.writeAndFlush(pingWebSocketFrame);
    }

    @Override
    public void sendPong() {
        PongWebSocketFrame pongWebSocketFrame = new PongWebSocketFrame(Unpooled.EMPTY_BUFFER);
        channel.writeAndFlush(pongWebSocketFrame);
    }

    @Override
    public void close() {
        channel.close();
    }

    @Override
    public ChannelFuture sendText(String message) {
        TextWebSocketFrame textWebSocketFrame = new TextWebSocketFrame(message);
        return channel.writeAndFlush(textWebSocketFrame);
    }

    @Override
    public ChannelFuture sendBinary(byte[] message) {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(message);
        return sendBinary(byteBuf);
    }

    @Override
    public ChannelFuture sendBinary(ByteBuf byteBuf) {
        BinaryWebSocketFrame binaryWebSocketFrame = new BinaryWebSocketFrame(byteBuf);
        return channel.writeAndFlush(binaryWebSocketFrame);
    }

    @Override
    public ChannelFuture send(WebSocketFrame webSocketFrame) {
        return channel.writeAndFlush(webSocketFrame);
    }
}
