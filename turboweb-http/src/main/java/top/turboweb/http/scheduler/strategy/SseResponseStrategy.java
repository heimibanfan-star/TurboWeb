package top.turboweb.http.scheduler.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.InternalSseEmitter;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.SseResponse;

/**
 * 用户处理SSE响应的策略实现类
 */
public class SseResponseStrategy extends ResponseStrategy {
    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        if (response instanceof SseResponse sseResponse) {
            // 发送响应头
            ChannelFuture channelFuture = session.getChannel().writeAndFlush(response);
            // 调用sse的回调函数
            sseResponse.startSse();
            return channelFuture;
        } else if (response instanceof InternalSseEmitter sseEmitter) {
            // 对SSE发射器进行初始化
            sseEmitter.initSse();
        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
        return null;
    }
}
