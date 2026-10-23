package top.turboweb.websocket.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.common.exception.TurboWebSocketException;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.websocket.info.WebSocketConnectInfo;
import top.turboweb.websocket.info.WebSocketConnectInfoContainer;
import top.turboweb.websocket.session.StandardWebSocketSession;
import top.turboweb.websocket.session.WebSocketSession;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理websocket的handler，消息回调经注入的微任务执行器异步执行。
 * <p>
 * 构造期传入 {@link MicroTaskExecutor}：入站帧在提交前自持引用，任务执行完毕与任务未受理
 * 两种收尾均由收尾回调统一归还该引用；任务未受理（执行器拒绝受理）时记录消息丢弃日志，
 * 拒绝异常照常向pipeline抛出。执行器的选型（非受限虚拟线程或工作窃取）与其生命周期回收
 * 归装配方管理，本handler不建线程池也不关闭执行器。
 * </p>
 */
@ChannelHandler.Sharable
public class WebSocketDispatcherChannelHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(WebSocketDispatcherChannelHandler.class);
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>(1024);
    private final WebSocketHandler webSocketHandler;
    /**
     * 消息处理微任务执行器：由装配方创建并注入，onMessage任务经其submit执行
     */
    private final MicroTaskExecutor microTaskExecutor;

    public WebSocketDispatcherChannelHandler(WebSocketHandler webSocketHandler, MicroTaskExecutor microTaskExecutor) {
        this.webSocketHandler = webSocketHandler;
        this.microTaskExecutor = Objects.requireNonNull(microTaskExecutor, "microTaskExecutor must not be null");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, WebSocketFrame webSocketFrame) throws Exception {
        WebSocketSession webSocketSession = getWebSocketSession(channelHandlerContext);
        // 使用前自持引用，防止被当前处理器给释放内存；任务收尾时统一归还
        webSocketFrame.retain();
        microTaskExecutor.submit(
                () -> webSocketHandler.onMessage(webSocketSession, webSocketFrame),
                signal -> {
                    try {
                        if (signal.finallyType() == MicroTaskExecutor.FinallyType.NOT_ACCEPT) {
                            log.warn("websocket message discarded: micro task executor did not accept the submission");
                        }
                    } finally {
                        // 执行完毕与未受理两路收尾统一归还本处自持的引用
                        webSocketFrame.release();
                    }
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
