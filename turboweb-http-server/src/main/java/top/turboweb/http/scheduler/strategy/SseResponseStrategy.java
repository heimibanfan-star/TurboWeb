package top.turboweb.http.scheduler.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.InternalSseEmitter;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.SseResponse;

/**
 * <p><b>SSE（Server-Sent Events）响应策略实现类。</b></p>
 *
 * <p>
 * 该策略专用于处理基于 {@link SseResponse} 和 {@link SseEmitter} 的
 * 服务端推送响应（Server-Sent Events）。
 * 当框架检测到响应类型为 SSE 时，将由此策略接管输出逻辑，
 * 建立持久连接并启动消息推送流。
 * </p>
 *
 * <p><b>职责：</b></p>
 * <ul>
 *     <li>在响应阶段初始化 SSE 通道并发送初始响应头。</li>
 *     <li>触发 {@link SseResponse#startSse()} 启动推送回调。</li>
 *     <li>当响应为内部发射器 {@link InternalSseEmitter} 时，负责完成 SSE 初始化。</li>
 * </ul>
 *
 * <p><b>异常处理：</b></p>
 * 若响应类型不属于 {@link SseResponse} 或 {@link InternalSseEmitter}，
 * 将抛出 {@link IllegalArgumentException} 以提示调用方使用了不支持的响应类型。
 * </p>
 */
public class SseResponseStrategy extends ResponseStrategy {

    /**
     * 处理 SSE 类型的响应逻辑。
     *
     * @param response 响应对象，可为 {@link SseResponse} 或 {@link InternalSseEmitter}
     * @param session  当前连接会话，封装了底层 Netty 通道
     * @return 写入操作的 {@link ChannelFuture}（内部发射器类型可能返回 null）
     * @throws IllegalArgumentException 当响应类型不支持时抛出
     */
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
