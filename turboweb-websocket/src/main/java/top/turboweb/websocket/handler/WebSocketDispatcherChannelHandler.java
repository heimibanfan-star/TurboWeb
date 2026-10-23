package top.turboweb.websocket.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.common.exception.TurboWebSocketException;
import top.turboweb.common.utils.thread.VirtualThreads;
import top.turboweb.websocket.info.WebSocketConnectInfo;
import top.turboweb.websocket.info.WebSocketConnectInfoContainer;
import top.turboweb.websocket.session.StandardWebSocketSession;
import top.turboweb.websocket.session.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

/**
 * 处理websocket的handler
 */
@ChannelHandler.Sharable
public class WebSocketDispatcherChannelHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(WebSocketDispatcherChannelHandler.class);
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>(1024);
    private final WebSocketHandler webSocketHandler;
    private final ExecutorService POOL;
    /**
     * 执行器是否为本实例私有：ForkJoin分支创建的是私有线程池，须由 {@link #destroy()} 回收；
     * 虚拟线程分支复用JVM全局共享池，任何单个使用者都无权关闭
     */
    private final boolean ownExecutor;

    public WebSocketDispatcherChannelHandler(WebSocketHandler webSocketHandler) {
        this(webSocketHandler, false, 0);
    }

    public WebSocketDispatcherChannelHandler(WebSocketHandler webSocketHandler, boolean useForkJoin, int threadNum) {
        if (threadNum <= 0) {
            threadNum = Runtime.getRuntime().availableProcessors();
        }
        this.webSocketHandler = webSocketHandler;
        this.ownExecutor = useForkJoin;
        if (useForkJoin) {
            POOL = new ForkJoinPool(threadNum);
            log.info("websocket work on ForkJoin pool, threadNum: {}", threadNum);
        } else {
            POOL = VirtualThreads.getPool();
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

    /**
     * 销毁分发处理器持有的执行资源：仅在本实例私有时关闭 {@link ForkJoinPool}
     * （已提交的消息任务继续执行完），虚拟线程模式下持有的是 {@link VirtualThreads#getPool()}
     * 全局共享池，不做关闭。重复调用为幂等操作。
     */
    public void destroy() {
        if (ownExecutor && !POOL.isShutdown()) {
            POOL.shutdown();
            log.info("websocket ForkJoinPool is shutdown");
        }
    }
}
