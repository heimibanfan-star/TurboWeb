package top.turboweb.http.scheduler.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connect.InternalConnectSession;

/**
 * 响应忽略策略
 */
public class IgnoredResponseStrategy extends ResponseStrategy {
    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        return null;
    }
}
