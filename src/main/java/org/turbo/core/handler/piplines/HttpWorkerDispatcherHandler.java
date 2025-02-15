package org.turbo.core.handler.piplines;

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
import org.turbo.core.http.execetor.impl.DefaultHttpExecuteAdaptor;
import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.core.http.response.HttpInfoResponse;
import org.turbo.utils.http.HttpInfoRequestPackageUtils;
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
                HttpInfoResponse response = httpExecuteAdaptor.doExecutor(fullHttpRequest);
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
                log.error("业务逻辑处理失败", future.cause());
                // 创建响应对象
                HttpInfoResponse response = new HttpInfoResponse(fullHttpRequest.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
                Map<String, String> errorMap = new HashMap<>();
                errorMap.put("code", "500");
                errorMap.put("msg", future.cause().getMessage());
                String errorMsg = objectMapper.writeValueAsString(errorMap);
                response.setContent(errorMsg);
                response.setContentType("application/json");
                channelHandlerContext.writeAndFlush(response);
            }
            // 释放资源
            fullHttpRequest.release();
        });
    }
}
