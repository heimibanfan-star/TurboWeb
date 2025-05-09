package org.turbo.web.core.handler.piplines;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.connect.InternalConnectSession;
import org.turbo.web.core.gateway.Gateway;
import org.turbo.web.core.http.scheduler.HttpScheduler;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.connect.ConnectSession;
import org.turbo.web.core.http.ws.PathWebSocketPreInit;
import org.turbo.web.core.http.ws.WebSocketConnectInfo;
import org.turbo.web.core.http.ws.WebSocketConnectInfoContainer;
import org.turbo.web.core.http.ws.WebSocketPreInit;
import org.turbo.web.exception.TurboRouterException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 转交http请求
 */
@ChannelHandler.Sharable
public class HttpWorkerDispatcherHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(HttpWorkerDispatcherHandler.class);
    private final HttpScheduler httpScheduler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketDispatcherHandler webSocketDispatcherHandler;
    private final WebSocketPreInit webSocketPreInit;
    private final Gateway gateway;

    public HttpWorkerDispatcherHandler(
        HttpScheduler httpScheduler,
        WebSocketDispatcherHandler webSocketDispatcherHandler,
        String websocketPath,
        Gateway gateway
    ) {
        this.httpScheduler = httpScheduler;
        this.webSocketDispatcherHandler = webSocketDispatcherHandler;
        this.webSocketPreInit = new PathWebSocketPreInit(websocketPath, webSocketDispatcherHandler);
        this.gateway = gateway;
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
        // 获取当前管道绑定的eventLoop
        EventLoop eventLoop = channelHandlerContext.channel().eventLoop();
        // 创建异步对象
        Promise<HttpResponse> promise = eventLoop.newPromise();
        // 增加引用，防止被房前处理器给释放内存
        fullHttpRequest.retain();
        // 封装连接的会话对象
        InternalConnectSession connectSession = new InternalConnectSession(channelHandlerContext.channel());
        // 执行异步任务
        httpScheduler.execute(fullHttpRequest, connectSession);
        // 监听业务逻辑处理完成
        promise.addListener(future -> {
            try {
                // 判断成功
                if (future.isSuccess()) {
                    channelHandlerContext.writeAndFlush(future.getNow());
                } else {
                    HttpInfoResponse response = doNotHandleException(fullHttpRequest, future.cause());
                    channelHandlerContext.writeAndFlush(response);
                }
            } finally {
                // 释放资源
                fullHttpRequest.release();
            }
        });
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

    /**
     * 处理异常
     *
     * @param request 请求对象
     * @param cause   异常
     * @return 响应对象
     */
    private HttpInfoResponse doNotHandleException(FullHttpRequest request, Throwable cause) throws JsonProcessingException {
        log.error("业务逻辑处理失败", cause);
        Map<String, String> errorMsg = new HashMap<>();
        if (cause instanceof TurboRouterException exception && Objects.equals(exception.getCode(), TurboRouterException.ROUTER_NOT_MATCH)) {
            HttpInfoResponse response = new HttpInfoResponse(request.protocolVersion(), HttpResponseStatus.NOT_FOUND);
            errorMsg.put("code", "404");
            errorMsg.put("msg", "Router Handler Not Found For: %s %s".formatted(request.method(), request.uri()));
            response.setContent(objectMapper.writeValueAsString(errorMsg));
            response.setContentType("application/json");
            return response;
        } else {
            HttpInfoResponse response = new HttpInfoResponse(request.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
            errorMsg.put("code", "500");
            errorMsg.put("msg", cause.getMessage());
            response.setContent(objectMapper.writeValueAsString(errorMsg));
            response.setContentType("application/json");
            return response;
        }
    }

//    @Override
//    public void channelActive(ChannelHandlerContext ctx) throws Exception {
//        // 获取通道的唯一标识
//        ChannelId channelId = ctx.channel().id();
//        // 创建promise对象
//        Promise<Boolean> promise = new DefaultPromise<>(ctx.executor());
//        // 存入容器
//        HttpConnectPromiseContainer.put(channelId.asLongText(), promise);
//        // 调用后续的处理器
//        ctx.fireChannelActive();
//    }

//    @Override
//    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
//        // 获取通道的唯一标识
//        ChannelId channelId = ctx.channel().id();
//        // 从容器中获取promise对象
//        Promise<Boolean> promise = HttpConnectPromiseContainer.get(channelId.asLongText());
//        if (promise != null) {
//            promise.setSuccess(true);
//            HttpConnectPromiseContainer.remove(channelId.asLongText());
//        }
//        ctx.fireChannelInactive();
//    }
}
