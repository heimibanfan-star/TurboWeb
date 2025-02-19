package org.turbo.core.handler.piplines;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.core.http.execetor.HttpExecuteAdaptor;
import org.turbo.core.http.response.HttpInfoResponse;
import org.turbo.exception.TurboRouterNotMatchException;
import org.turbo.utils.thread.LoomThreadUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 转交http请求
 */
@ChannelHandler.Sharable
public class HttpWorkerDispatcherHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(HttpWorkerDispatcherHandler.class);
    private final HttpExecuteAdaptor httpExecuteAdaptor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpWorkerDispatcherHandler(HttpExecuteAdaptor httpExecuteAdaptor) {
        this.httpExecuteAdaptor = httpExecuteAdaptor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) throws Exception {
        // 获取当前管道绑定的eventLoop
        EventLoop eventLoop = channelHandlerContext.channel().eventLoop();
        // 创建异步对象
        Promise<HttpInfoResponse> promise = eventLoop.newPromise();
        // 增加引用，防止被房前处理器给释放内存
        fullHttpRequest.retain();
        // 执行异步任务
        LoomThreadUtils.execute(() -> {
            try {
                HttpInfoResponse response = httpExecuteAdaptor.execute(fullHttpRequest);
                promise.setSuccess(response);
            } catch (Throwable throwable) {
                promise.setFailure(throwable);
            }
        });
        // 监听业务逻辑处理完成
        promise.addListener(future -> {
            // 判断成功
            if (future.isSuccess()) {
                channelHandlerContext.writeAndFlush(future.getNow());
            } else {
                HttpInfoResponse response = doNotHandleException(fullHttpRequest, future.cause());
                channelHandlerContext.writeAndFlush(response);
            }
            // 释放资源
            fullHttpRequest.release();
        });
    }

    /**
     * 处理异常
     *
     * @param request 请求对象
     * @param cause 异常
     * @return 响应对象
     */
    private HttpInfoResponse doNotHandleException(FullHttpRequest request, Throwable cause) throws JsonProcessingException {
        log.error("业务逻辑处理失败", cause);
        Map<String, String> errorMsg = new HashMap<>();
        if (cause instanceof TurboRouterNotMatchException) {
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
}
