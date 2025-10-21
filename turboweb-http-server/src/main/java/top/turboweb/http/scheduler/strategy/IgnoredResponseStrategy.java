package top.turboweb.http.scheduler.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connect.InternalConnectSession;

/**
 * 响应忽略策略。
 * <p>
 * 当某个请求的响应无需发送到客户端，或者上层逻辑已自行处理响应，可使用该策略作为空操作响应策略。
 * </p>
 *
 * <p>
 * 此策略不会执行任何写入或刷新操作，仅返回 {@code null}，表示该响应已被忽略。
 * 由上层调用者根据返回值决定是否继续处理。
 * </p>
 */
public class IgnoredResponseStrategy extends ResponseStrategy {
    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        return null;
    }
}
