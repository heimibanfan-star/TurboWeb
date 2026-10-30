package top.turboweb.http.orchestrator.writer;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connection.InternalHttpConnection;
import top.turboweb.http.response.InternalSseEmitter;
import top.turboweb.http.response.SseEmitter;

/**
 * SSE（Server-Sent Events）响应写出器。
 * <p>
 * 该写出器专用于处理基于 {@link SseEmitter} 的服务端推送响应，
 * 负责建立持久连接并启动消息推送流。
 * </p>
 *
 * <p><b>职责：</b></p>
 * <ul>
 *     <li>当响应为内部发射器 {@link InternalSseEmitter} 时，发送初始响应头并完成 SSE 初始化。</li>
 * </ul>
 *
 * <p><b>异常处理：</b></p>
 * 若响应类型不属于 {@link InternalSseEmitter}，
 * 将抛出 {@link IllegalArgumentException} 以提示调用方使用了不支持的响应类型。
 */
public class SseResponseWriter extends AbstractResponseWriter {

    /**
     * 处理 SSE 类型的响应逻辑。
     *
     * @param response 响应对象，须为 {@link InternalSseEmitter}
     * @param session  当前连接会话，封装了底层 Netty 通道
     * @return 写入操作的 {@link ChannelFuture}（内部发射器类型返回 null）
     * @throws IllegalArgumentException 当响应类型不支持时抛出
     */
    @Override
    protected ChannelFuture doWrite(HttpResponse response, InternalHttpConnection session) {
        if (response instanceof InternalSseEmitter sseEmitter) {
            // 对SSE发射器进行初始化
            sseEmitter.initSse();
        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
        return null;
    }
}
