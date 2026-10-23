package top.turboweb.websocket.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.common.exception.TurboWebSocketException;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.websocket.session.StandardWebSocketSession;
import top.turboweb.websocket.session.WebSocketSession;

import java.util.Objects;

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
public class WebSocketChannelHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(WebSocketChannelHandler.class);
    /**
     * 会话挂载在channel附件上的键：一个channel对应一个会话，握手完成时挂载，随channel自然回收
     */
    private static final AttributeKey<WebSocketSession> SESSION_KEY = AttributeKey.valueOf("top.turboweb.websocket.session");
    private final WebSocketHandler webSocketHandler;
    /**
     * 消息处理微任务执行器：由装配方创建并注入，入站帧的处理任务经其submit执行
     */
    private final MicroTaskExecutor microTaskExecutor;

    public WebSocketChannelHandler(WebSocketHandler webSocketHandler, MicroTaskExecutor microTaskExecutor) {
        this.webSocketHandler = webSocketHandler;
        this.microTaskExecutor = Objects.requireNonNull(microTaskExecutor, "microTaskExecutor must not be null");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, WebSocketFrame webSocketFrame) throws Exception {
        WebSocketSession webSocketSession = getWebSocketSession(channelHandlerContext);
        // 使用前自持引用，防止被当前处理器给释放内存；任务收尾时统一归还
        webSocketFrame.retain();
        microTaskExecutor.submit(
                () -> dispatch(webSocketSession, webSocketFrame),
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
     * 按帧的具体类型分派到对应的裸帧回调，文本、二进制、ping、pong 之外的帧不做处理。
     *
     * @param session        websocket会话
     * @param webSocketFrame 入站帧
     */
    private void dispatch(WebSocketSession session, WebSocketFrame webSocketFrame) {
        if (webSocketFrame instanceof TextWebSocketFrame textWebSocketFrame) {
            webSocketHandler.onUnSafeText(session, textWebSocketFrame);
        } else if (webSocketFrame instanceof BinaryWebSocketFrame binaryWebSocketFrame) {
            webSocketHandler.onUnSafeBinary(session, binaryWebSocketFrame);
        } else if (webSocketFrame instanceof PingWebSocketFrame pingWebSocketFrame) {
            webSocketHandler.onUnSafePing(session, pingWebSocketFrame);
        } else if (webSocketFrame instanceof PongWebSocketFrame pongWebSocketFrame) {
            webSocketHandler.onUnSafePong(session, pongWebSocketFrame);
        }
    }

    /**
     * 获取websocket的session
     *
     * @param ctx 管道上下文
     */
    private WebSocketSession getWebSocketSession(ChannelHandlerContext ctx) {
        // 获取挂载在channel附件上的session
        WebSocketSession webSocketSession = ctx.channel().attr(SESSION_KEY).get();
        if (webSocketSession == null) {
            // 关闭管道
            ctx.channel().close();
            // 会话尚未建立
            throw new TurboWebSocketException("websocket会话不存在");
        }
        return webSocketSession;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            // 摘除挂载在channel附件上的session（仅为避免后续误读，附件本身随channel回收）
            WebSocketSession webSocketSession = ctx.channel().attr(SESSION_KEY).getAndSet(null);
            // 调用close方法
            webSocketHandler.onClose(webSocketSession);
        } finally {
            ctx.fireChannelInactive();
        }
    }

    /**
     * 通知websocket握手完成
     *
     * @param ctx               管道上下文
     * @param handshakeComplete 握手完成事件，携带握手请求的uri与请求头
     */
    public void noticeFinishShakeHand(ChannelHandlerContext ctx, WebSocketServerProtocolHandler.HandshakeComplete handshakeComplete) {
        // 以握手请求的uri与请求头创建websocket的会话，挂载到channel附件上
        WebSocketSession webSocketSession = new StandardWebSocketSession(
                ctx.channel(), handshakeComplete.requestUri(), handshakeComplete.requestHeaders());
        ctx.channel().attr(SESSION_KEY).set(webSocketSession);
        // 调用open方法
        webSocketHandler.onOpen(webSocketSession);
    }
}
