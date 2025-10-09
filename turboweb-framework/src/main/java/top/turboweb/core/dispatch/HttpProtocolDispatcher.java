package top.turboweb.core.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.http.connect.InternalConnectSession;
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

    public HttpProtocolDispatcher(
        HttpScheduler httpScheduler,
        WebSocketDispatcherHandler webSocketDispatcherHandler,
        String websocketPath
    ) {
        this.httpScheduler = httpScheduler;
        this.webSocketDispatcherHandler = webSocketDispatcherHandler;
        this.webSocketPreInit = new PathWebSocketPreInit(websocketPath, webSocketDispatcherHandler);
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
        // 增加引用，防止被房前处理器给释放内存
        fullHttpRequest.retain();
        // 封装连接的会话对象
        InternalConnectSession connectSession = new InternalConnectSession(channelHandlerContext.channel());
        // 执行异步任务
        httpScheduler.execute(fullHttpRequest, connectSession);
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
}
