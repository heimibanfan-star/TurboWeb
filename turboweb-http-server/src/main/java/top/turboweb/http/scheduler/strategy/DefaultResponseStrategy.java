package top.turboweb.http.scheduler.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connect.InternalConnectSession;

/**
 * 默认的响应策略。
 * <p>
 * 当所有自定义 {@link ResponseStrategy} 都无法匹配当前响应时，
 * 将由该策略进行兜底处理，保证响应能够被正常写出。
 * </p>
 *
 * <p>
 * 该策略会在发送前检查响应头中是否包含 {@code Content-Length}，
 * 若未设置且响应为 {@link FullHttpResponse}，则自动补充内容长度；
 * 否则默认设置为 {@code 0}，以确保 HTTP 报文完整性。
 * </p>
 *
 * <p>
 * 最终通过 {@link InternalConnectSession#getChannel()} 将响应写出并刷新。
 * </p>
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
