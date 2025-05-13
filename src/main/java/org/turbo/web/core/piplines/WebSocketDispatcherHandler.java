package org.turbo.web.core.piplines;

import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.http.ws.*;
import org.turbo.web.exception.TurboWebSocketException;
import org.turbo.web.utils.thread.VirtualThreadUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

/**
 * 处理websocket的handler
 */
@ChannelHandler.Sharable
public class WebSocketDispatcherHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(WebSocketDispatcherHandler.class);
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>(1024);
    private final WebSocketHandler webSocketHandler;
    private final ExecutorService POOL;

    public WebSocketDispatcherHandler(WebSocketHandler webSocketHandler) {
        this(webSocketHandler, false, 0);
    }

    public WebSocketDispatcherHandler(WebSocketHandler webSocketHandler, boolean useForkJoin, int threadNum) {
        if (threadNum <= 0) {
            threadNum = Runtime.getRuntime().availableProcessors();
        }
        this.webSocketHandler = webSocketHandler;
        if (useForkJoin) {
            POOL = new ForkJoinPool(threadNum);
            log.info("websocket work on ForkJoin pool, threadNum: {}", threadNum);
        } else {
            POOL = VirtualThreadUtils.getPool();
            log.info("websocket work on VirtualThread");
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, WebSocketFrame webSocketFrame) throws Exception {
        WebSocketSession webSocketSession = getWebSocketSession(channelHandlerContext);
        webSocketFrame.retain();
        POOL.execute(() -> {
            // 调度处理器
            webSocketHandler.onMessage(webSocketSession, webSocketFrame);
        });
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
            // 关闭管道
            ctx.channel().close();
            // 连接信息失效
            throw new TurboWebSocketException("websocket连接信息为空");
        }
        return webSocketSession;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 获取管道的id
        ChannelId channelId = ctx.channel().id();
        try {
            // 获取session
            WebSocketSession webSocketSession = sessionMap.remove(channelId.asLongText());
            WebSocketConnectInfoContainer.removeWebSocketConnectInfo(channelId.asLongText());
            // 调用close方法
            webSocketHandler.onClose(webSocketSession);
        } finally {
            ctx.fireChannelInactive();
        }
    }

    /**
     * 通知websocket握手完成
     *
     * @param ctx 管道上下文
     */
    public void noticeFinishShakeHand(ChannelHandlerContext ctx) {
        // 获取websocket的连接信息
        WebSocketConnectInfo connectInfo = WebSocketConnectInfoContainer.getWebSocketConnectInfo(ctx.channel().id().asLongText());
        if (connectInfo == null) {
            // 关闭channel
            ctx.channel().close();
            throw new TurboWebSocketException("websocket连接信息为空");
        }
        // 创建websocket的回话
        WebSocketSession webSocketSession = new StandardWebSocketSession(ctx.channel(), connectInfo);
        sessionMap.put(ctx.channel().id().asLongText(), webSocketSession);
        // 调用open方法
        webSocketHandler.onOpen(webSocketSession);
    }
}

