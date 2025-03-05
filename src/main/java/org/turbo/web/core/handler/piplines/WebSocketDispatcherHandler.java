package org.turbo.web.core.handler.piplines;

import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.turbo.web.core.http.ws.StandardWebSocketSession;
import org.turbo.web.core.http.ws.WebSocketHandler;
import org.turbo.web.core.http.ws.WebSocketSession;
import org.turbo.web.exception.TurboWebSocketException;
import org.turbo.web.utils.thread.LoomThreadUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理websocket的handler
 */
@ChannelHandler.Sharable
public class WebSocketDispatcherHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>(1024);
    private final WebSocketHandler webSocketHandler;

    public WebSocketDispatcherHandler(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, WebSocketFrame webSocketFrame) throws Exception {
        // 判断websocket的帧的类型
        if (webSocketFrame instanceof TextWebSocketFrame textWebSocketFrame) {
            WebSocketSession webSocketSession = getWebSocketSession(channelHandlerContext);
            // 获取收到的消息
            String message = textWebSocketFrame.text();
            LoomThreadUtils.execute(() ->{
                // 调度处理器
                webSocketHandler.onMessage(webSocketSession, message);
            });
        } else if (webSocketFrame instanceof PingWebSocketFrame) {
            WebSocketSession webSocketSession = getWebSocketSession(channelHandlerContext);
            LoomThreadUtils.execute(() ->{
                webSocketHandler.onPing(webSocketSession);
            });
        } else if (webSocketFrame instanceof PongWebSocketFrame) {
            WebSocketSession webSocketSession = getWebSocketSession(channelHandlerContext);
            LoomThreadUtils.execute(() ->{
                webSocketHandler.onPong(webSocketSession);
            });
        }
    }

    /**
     * 获取websocket的session
     *
     * @param ctx 管道上下文
     * @return org.turbo.web.core.http.ws.WebSocketSession
     */
    private WebSocketSession getWebSocketSession(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asLongText();
        // 获取建立的session
        WebSocketSession webSocketSession = sessionMap.get(channelId);
        if (webSocketSession == null) {
            // 锁住当前管道
                synchronized (channelId.intern()) {
                webSocketSession = sessionMap.get(channelId);
                if (webSocketSession == null) {
                    webSocketSession = new StandardWebSocketSession(ctx.channel().eventLoop(), ctx.channel());
                    sessionMap.put(channelId, webSocketSession);
                    webSocketHandler.onOpen(webSocketSession);
                }
            }
        }
        return webSocketSession;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 获取管道的id
        ChannelId channelId = ctx.channel().id();
        try {
            // 获取session
            WebSocketSession webSocketSession = sessionMap.get(channelId.asLongText());
            // 调用close方法
            webSocketHandler.onClose(webSocketSession);
        } finally {
            sessionMap.remove(channelId.asLongText());
        }
        ctx.fireChannelInactive();
    }
}

