package top.turboweb.http.orchestrator.writer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.http.connection.HttpConnection;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.response.StreamResponseMessage;

import java.util.Optional;

/**
 * 流式响应消息写出处理器：把 {@link StreamResponseMessage} 持有的响应体流逐块写出到连接上。
 * <p>
 * 写出按响应首部、逐块响应体、结束标记的次序推进：首部写出完成后才订阅流并发出首块，
 * 流每产出一块即经连接写出，上一块的写出凭据完成后才发出下一块（在途窗口为 1），
 * 凭据在数据被管道暂扣期间不完成，上游随即停住，背压由此闭合；
 * 流正常结束后写出结束标记。首部未携带传输编码头时补齐 chunked 分块传输声明，
 * 已携带时不覆写；消息体长度由终止块划定，已携带的 Content-Length 头在写出前剔除。
 * </p>
 * <p>
 * 首部写出失败、某块写出失败、结束标记写出失败或流出错时先关闭通道再上报失败：
 * 失败后响应帧序已残缺，关闭通道防止半截响应与后续响应在连接复用时交错；
 * 上报失败并取消流，不再写出后续块与结束标记。
 * 写出结果经回调恰好上报一次，以 FINISHED 报告正常结束（成功或失败）。
 * </p>
 */
public final class StreamMessageWriteHandler implements MessageWriteHandler {

    /** 传输编码头名 */
    private static final String TRANSFER_ENCODING = "Transfer-Encoding";
    /** 分块传输编码取值 */
    private static final String CHUNKED = "chunked";
    /** 消息体长度头名：与分块传输互斥，长度由终止块划定 */
    private static final String CONTENT_LENGTH = "Content-Length";

    @Override
    public void write(HttpConnection connection, HttpResponseMessage<?> message, Callback callback) {
        if (!(message instanceof StreamResponseMessage streamMessage)) {
            throw new IllegalArgumentException("Invalid message type: " + message.getClass().getName());
        }
        if (!streamMessage.containsHeader(TRANSFER_ENCODING)) {
            streamMessage.setHeader(TRANSFER_ENCODING, CHUNKED);
        }
        streamMessage.removeHeader(CONTENT_LENGTH);
        Flux<byte[]> content = Optional.ofNullable(streamMessage.content()).orElse(Flux.empty());
        Mono.fromFuture(() -> connection.writeHeaders(streamMessage.statusLine(), streamMessage.headers()))
                .then(content
                        .concatMap(chunk -> Mono.fromFuture(connection.write(chunk)), 1)
                        .then())
                .then(Mono.fromFuture(connection::finishWrite))
                .subscribe(null,
                        // 失败帧序已残缺，先关闭通道防连接复用时半截响应与后续响应交错，再上报失败
                        error -> {
                            connection.close();
                            callback.onFinally(error, CallbackType.FINISHED);
                        },
                        () -> callback.onFinally(null, CallbackType.FINISHED));
    }
}
