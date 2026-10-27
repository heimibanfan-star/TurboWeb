package top.turboweb.http.core.response;

import io.netty.handler.codec.http.*;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.http.connection.HttpConnection;
import top.turboweb.http.orchestrator.ResponseWriter;
import top.turboweb.http.orchestrator.SseResponseWriter;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Server-Sent Events (SSE) 响应对象，用于通过 HTTP 连接向客户端推送实时事件流。
 * <p>
 * 该类继承 {@link DefaultHttpResponse}，支持 SSE 的标准响应头设置，
 * 并提供多种方式注册 SSE 数据流回调。
 * </p>
 */
public class SseResponse extends DefaultHttpResponse implements ResponseCall {

    private final HttpConnection connectSession;
    private Consumer<HttpConnection> sseCallback;
    private final JsonSerializer jsonSerializer;

    /**
     * 构造 SSE 响应对象。
     *
     * @param status         HTTP 响应状态
     * @param headers        HTTP 响应头
     * @param connectSession 当前 SSE 连接会话
     * @param jsonSerializer JSON 序列化器
     */
    public SseResponse(HttpResponseStatus status, HttpHeaders headers, HttpConnection connectSession, JsonSerializer jsonSerializer) {
        super(HttpVersion.HTTP_1_1, status, headers);
        assert connectSession != null;
        this.connectSession = connectSession;
        this.setSseHeaders();
        this.jsonSerializer = jsonSerializer;
    }

    /**
     * 设置 SSE 必需的 HTTP 头，包括：
     * <ul>
     *     <li>Content-Type: text/event-stream（charset 段由结果转化环节按缺省字符集补写）</li>
     *     <li>Cache-Control: no-cache</li>
     *     <li>Connection: keep-alive</li>
     *     <li>Transfer-Encoding: chunked</li>
     * </ul>
     */
    private void setSseHeaders() {
        this.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        this.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        this.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
        this.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
        HttpUtil.setTransferEncodingChunked(this, true); // 开启 Chunked 传输
    }

    /**
     * 设置 SSE 的回调函数。
     * <p>框架会在合适时机调用 {@link #startSse()} 执行该回调。</p>
     *
     * @param sseCallback SSE 回调函数，接收当前 {@link HttpConnection}
     */
    public void setSseCallback(Consumer<HttpConnection> sseCallback) {
        this.sseCallback = sseCallback;
    }

    /**
     * 启动 SSE，触发已注册的 SSE 回调。
     * <p>此方法由框架自动调用，开发者无需手动调用。</p>
     */
    public void startSse() {
        if (sseCallback != null) {
            sseCallback.accept(connectSession);
        }
    }

    /**
     * 设置 SSE 回调函数，并绑定数据流 {@link Flux} 与错误处理逻辑。
     *
     * @param flux         SSE 的数据流
     * @param errorHandler 错误处理函数，可将异常转换为发送给客户端的字符串
     * @param onFinally    当数据流结束时执行的回调
     */
    public void setSseCallback(Flux<String> flux, Function<Throwable, String> errorHandler, Consumer<HttpConnection> onFinally) {
        Consumer<HttpConnection> consumer = (session) -> {
            flux.doFinally(signalType -> {
                        if (onFinally != null) {
                            onFinally.accept(session);
                        }
                    })
                    .subscribe(
                            message -> session.write(message.getBytes(StandardCharsets.UTF_8)),
                            err -> {
                                if (errorHandler != null) {
                                    String errorMessage = errorHandler.apply(err);
                                    session.write(errorMessage.getBytes(StandardCharsets.UTF_8));
                                } else {
                                    session.write(("error:" + err.getMessage()).getBytes(StandardCharsets.UTF_8));
                                }
                            }
                    );
        };
        this.setSseCallback(consumer);
    }


    /**
     * 设置 SSE 回调函数，使用默认错误处理逻辑（关闭连接）。
     *
     * @param flux SSE 的数据流
     */
    public void setSseCallback(Flux<String> flux) {
        this.setSseCallback(flux, null, HttpConnection::close);
    }

    public void setFlux(Flux<String> flux) {
        this.setSseCallback(flux.map(
                msg -> "data:" + msg + "\n\n"
        ));
    }

    @Override
    public Class<? extends ResponseWriter> getWriterType() {
        return SseResponseWriter.class;
    }
}
