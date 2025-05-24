package org.turboweb.core.http.ws;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * websocket的前置初始化器
 */
public interface WebSocketPreInit {

    void handle(ChannelHandlerContext ctx, FullHttpRequest request);
}
