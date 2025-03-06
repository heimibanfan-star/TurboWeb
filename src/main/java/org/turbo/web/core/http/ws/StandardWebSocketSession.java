package org.turbo.web.core.http.ws;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * 封装websocket的回话对象
 */
public class StandardWebSocketSession implements WebSocketSession{

    private final EventLoop eventLoop;
    private final Channel channel;
    private final WebSocketConnectInfo webSocketConnectInfo;

    public StandardWebSocketSession(EventLoop eventLoop, Channel channel, WebSocketConnectInfo connectInfo) {
        this.eventLoop = eventLoop;
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
        eventLoop.execute(() -> channel.writeAndFlush(pingWebSocketFrame));
    }

    @Override
    public void sendPong() {
        PongWebSocketFrame pongWebSocketFrame = new PongWebSocketFrame(Unpooled.EMPTY_BUFFER);
        eventLoop.execute(() -> channel.writeAndFlush(pongWebSocketFrame));
    }

    @Override
    public void close() {
        eventLoop.execute(channel::close);
    }

    @Override
    public void sendMessage(String message) {
        TextWebSocketFrame textWebSocketFrame = new TextWebSocketFrame(message);
        eventLoop.execute(() -> channel.writeAndFlush(textWebSocketFrame));
    }
}
