package top.turboweb.http.scheduler.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import top.turboweb.commons.utils.thread.ThreadAssert;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.ZeroCopyResponse;

import java.util.concurrent.ExecutionException;

/**
 * 响应零拷贝文件的策略
 */
public class ZeroCopyResponseStrategy extends ResponseStrategy{
    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        // 确保当前线程为虚拟线程
        ThreadAssert.assertIsVirtualThread();
        if (response instanceof ZeroCopyResponse zeroCopyResponse) {
            ChannelPromise promise = session.getChannel().newPromise();
            try {
                // 写入响应头
                session.getChannel().writeAndFlush(zeroCopyResponse).get();
                // 写入数据部分
                session.getChannel().writeAndFlush(zeroCopyResponse.getFileRegion()).get();
                // 写入结束标识
                session.getChannel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                        .addListener(future -> {
                           if (future.isSuccess()) {
                               promise.setSuccess();
                           } else {
                               promise.setFailure(future.cause());
                               session.close();
                           }
                        });
            } catch (InterruptedException | ExecutionException e) {
                promise.setFailure(e);
                session.close();
            }
            return promise;
        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
    }
}
