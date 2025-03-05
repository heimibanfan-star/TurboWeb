package org.turbo.web.core.http.ws;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * 封装websocket的回话对象
 */
public class StandardWebSocketSession implements WebSocketSession{

    private final EventLoop eventLoop;
    private final Channel channel;

    public StandardWebSocketSession(EventLoop eventLoop, Channel channel) {
        this.eventLoop = eventLoop;
        this.channel = channel;
    }

    @Override
    public void sendMessage(String message) {
        TextWebSocketFrame textWebSocketFrame = new TextWebSocketFrame(message);
        eventLoop.execute(() -> channel.writeAndFlush(textWebSocketFrame));
    }
}
