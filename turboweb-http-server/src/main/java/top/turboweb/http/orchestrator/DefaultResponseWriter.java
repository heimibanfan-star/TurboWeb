package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connection.InternalHttpConnection;

/**
 * 默认的响应写出器。
 * <p>
 * 当响应未声明写出器类型、或声明类型在内置表与自定义登记表中均未命中时，
 * 由该写出器进行兜底处理，保证响应能够被正常写出。
 * </p>
 *
 * <p>
 * 该写出器会在发送前检查响应头中是否包含 {@code Content-Length}，
 * 若未设置且响应为 {@link FullHttpResponse}，则自动补充内容长度；
 * 否则默认设置为 {@code 0}，以确保 HTTP 报文完整性。
 * </p>
 */
public class DefaultResponseWriter extends AbstractResponseWriter {

    @Override
    protected ChannelFuture doWrite(HttpResponse response, InternalHttpConnection session) {
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
