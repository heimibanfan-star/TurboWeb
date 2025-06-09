package top.turboweb.core.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.senntinels.AutoDestructSentinel;
import top.turboweb.commons.senntinels.SchedulerSentinel;
import top.turboweb.commons.utils.base.HttpResponseUtils;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.gateway.Gateway;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.websocket.PathWebSocketPreInit;
import top.turboweb.websocket.WebSocketConnectInfo;
import top.turboweb.websocket.WebSocketConnectInfoContainer;
import top.turboweb.websocket.WebSocketPreInit;
import top.turboweb.websocket.dispatch.WebSocketDispatcherHandler;

/**
 * 转交http请求
 */
@ChannelHandler.Sharable
public class HttpProtocolDispatcher extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(HttpProtocolDispatcher.class);
    private final HttpScheduler httpScheduler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketDispatcherHandler webSocketDispatcherHandler;
    private final WebSocketPreInit webSocketPreInit;
    private final Gateway gateway;
    private final AutoDestructSentinel.EnableScheduler enableScheduler;

    public HttpProtocolDispatcher(
        HttpScheduler httpScheduler,
        WebSocketDispatcherHandler webSocketDispatcherHandler,
        String websocketPath,
        Gateway gateway,
        AutoDestructSentinel.EnableScheduler enableScheduler
    ) {
        this.httpScheduler = httpScheduler;
        this.webSocketDispatcherHandler = webSocketDispatcherHandler;
        this.webSocketPreInit = new PathWebSocketPreInit(websocketPath, webSocketDispatcherHandler);
        this.gateway = gateway;
        this.enableScheduler = enableScheduler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) throws Exception {
        if (webSocketDispatcherHandler != null) {
            // 判断是否是websocket协议
            if (fullHttpRequest.headers().contains(HttpHeaderNames.UPGRADE, "websocket", true)) {
                handleInitWebSocket(channelHandlerContext, fullHttpRequest);
                fullHttpRequest.retain();
                channelHandlerContext.fireChannelRead(fullHttpRequest);
                return;
            }
        }
        // 判断是否启用网关
        if (gateway != null) {
            String url = gateway.matchNode(fullHttpRequest.uri());
            if (url != null) {
                url = url + fullHttpRequest.uri();
                fullHttpRequest.retain();
                gateway.forwardRequest(url, fullHttpRequest, channelHandlerContext.channel());
                return;
            }
        }
        // 增加引用，防止被房前处理器给释放内存
        fullHttpRequest.retain();
        // 封装连接的会话对象
        InternalConnectSession connectSession = new InternalConnectSession(channelHandlerContext.channel());
        // 执行异步任务
        if (enableScheduler == null) {
            httpScheduler.execute(fullHttpRequest, connectSession);
        } else {
            // 获取自毁哨兵
            SchedulerSentinel schedulerSentinel = channelHandlerContext.channel().attr(AutoDestructSentinel.ATTRIBUTE_KEY).get();
            // 执行任务
            boolean submitted = schedulerSentinel.submitTask(() -> {
                httpScheduler.execute(fullHttpRequest, connectSession);
            });
            if (!submitted) {
                log.warn("http request too many");
            }
        }
    }

    /**
     * 初始化websocket请求
     *
     * @param request 请求对象
     */
    private void handleInitWebSocket(ChannelHandlerContext ctx, FullHttpRequest request) {
        // 初始化handler链
        webSocketPreInit.handle(ctx, request);
        String channelId = ctx.channel().id().asLongText();
        String uri = request.uri();
        WebSocketConnectInfo connectInfo = new WebSocketConnectInfo(uri);
        WebSocketConnectInfoContainer.putWebSocketConnectInfo(channelId, connectInfo);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 判断是否需要创建自毁哨兵
        if (enableScheduler != null) {
            SchedulerSentinel schedulerSentinel = new AutoDestructSentinel(
                    enableScheduler.initCapacity(),
                    enableScheduler.maxCapacity(),
                    ctx.channel().eventLoop()
            );
            // 绑定到管道
            ctx.channel().attr(AutoDestructSentinel.ATTRIBUTE_KEY).set(schedulerSentinel);
        }
        super.channelActive(ctx);
    }
}
