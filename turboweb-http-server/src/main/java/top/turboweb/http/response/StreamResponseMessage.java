package top.turboweb.http.response;

import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.core.StatusLine;
import top.turboweb.http.orchestrator.writer.MessageWriteHandler;
import top.turboweb.http.orchestrator.writer.StreamMessageWriteHandler;
import top.turboweb.http.orchestrator.writer.WriteHandlerSubscribe;
import reactor.core.publisher.Flux;

/**
 * 流式响应消息：响应体以字节块序列承载，由 {@link Flux} 异步逐块产出，
 * 本类只做持有，不订阅流，各访问器重复访问返回同一值。
 *
 * <p>响应状态由状态行承接；头部读写由 {@link HttpResponseMessage} 的基类承载。
 * 本消息声明由内置流式写出处理器在写出阶段订阅流并逐块写出。</p>
 */
public final class StreamResponseMessage extends HttpResponseMessage<Flux<byte[]>> implements WriteHandlerSubscribe {

    /**
     * 建立流式响应消息。
     *
     * @param statusLine 状态行，不能为空
     * @param content    响应体流，逐块产出字节块，按入参原样持有，不作复制，不能为空
     * @throws NullPointerException 当 {@code statusLine} 或 {@code content} 为 {@code null} 时抛出
     */
    public StreamResponseMessage(StatusLine statusLine, Flux<byte[]> content) {
        super(statusLine, content);
    }

    /**
     * 声明本消息由内置流式写出处理器写出。
     *
     * @return 内置流式写出处理器的类型
     */
    @Override
    public Class<? extends MessageWriteHandler> subscriber() {
        return StreamMessageWriteHandler.class;
    }
}
