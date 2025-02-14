package org.turbo.core.handler.piplines;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.concurrent.Promise;
import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.utils.http.HttpInfoRequestPackageUtils;

/**
 * 转交http请求
 */
public class HttpWorkerDispatcherHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) throws Exception {
        System.out.println(fullHttpRequest.getClass());
        // 获取当前管道绑定的eventLoop
        EventLoop eventLoop = channelHandlerContext.channel().eventLoop();
        // 创建异步对象
        Promise<?> promise = eventLoop.newPromise();
        HttpInfoRequestPackageUtils.packageRequest(fullHttpRequest);
        promise.addListener(future -> {

        });
    }
}
