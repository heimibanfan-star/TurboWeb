package org.turbo.web.core.http.ws;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.turbo.web.core.handler.piplines.WebSocketDispatcherHandler;

/**
 * 路径处理的初始化器
 */
public class PathWebSocketPreInit implements WebSocketPreInit {

    private final String path;
    private final WebSocketDispatcherHandler webSocketDispatcherHandler;

    public PathWebSocketPreInit(String path, WebSocketDispatcherHandler webSocketDispatcherHandler) {
        this.path = path;
        this.webSocketDispatcherHandler = webSocketDispatcherHandler;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        if (uri.matches(path)) {
            ChannelPipeline pipeline = ctx.pipeline();
            if (pipeline.get(WebSocketServerProtocolHandler.class) == null) {
                pipeline.addLast(new WebSocketServerProtocolHandler(uri) {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                            webSocketDispatcherHandler.noticeFinishShakeHand(ctx);
                        }
                        super.userEventTriggered(ctx, evt);
                    }
                });
                pipeline.addLast(webSocketDispatcherHandler);
            }
        }
    }
}
