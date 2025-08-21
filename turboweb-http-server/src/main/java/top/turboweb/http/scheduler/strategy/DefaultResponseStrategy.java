package top.turboweb.http.scheduler.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connect.InternalConnectSession;

/**
 * 默认的响应策略
 */
public class DefaultResponseStrategy extends ResponseStrategy {
    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        // 判断是否有响应长度
        if (!response.headers().contains("Content-Length")) {
            if (response instanceof FullHttpResponse fullHttpResponse) {
                // 获取请求体的长度
                response.headers().set("Content-Length", fullHttpResponse.content().readableBytes());
            } else {
                response.headers().set("Content-Length", 0);
            }
        }
        return session.getChannel().writeAndFlush(response);
    }
}
