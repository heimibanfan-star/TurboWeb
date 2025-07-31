package top.turboweb.http.scheduler.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.ZeroCopyResponse;

/**
 * 响应零拷贝文件的策略
 */
public class ZeroCopyResponseStrategy extends ResponseStrategy{
    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        if (response instanceof ZeroCopyResponse zeroCopyResponse) {
            session.getChannel().writeAndFlush(zeroCopyResponse);
            session.getChannel().writeAndFlush(zeroCopyResponse.getFileRegion());
            return session.getChannel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
    }
}
