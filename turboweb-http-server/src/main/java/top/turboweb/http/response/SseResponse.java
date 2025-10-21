package top.turboweb.http.response;

import io.netty.handler.codec.http.*;
import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.http.connect.ConnectSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Server-Sent Events (SSE) 响应对象，用于通过 HTTP 连接向客户端推送实时事件流。
 * <p>
 * 该类继承 {@link DefaultHttpResponse}，支持 SSE 的标准响应头设置，
 * 并提供多种方式注册 SSE 数据流回调。
 * </p>
 */
public class SseResponse extends DefaultHttpResponse implements InternalCallResponse {

	private final ConnectSession connectSession;
	private Consumer<ConnectSession> sseCallback;
	private final JsonSerializer jsonSerializer;

	/**
	 * 构造 SSE 响应对象。
	 *
	 * @param status        HTTP 响应状态
	 * @param headers       HTTP 响应头
	 * @param connectSession 当前 SSE 连接会话
	 * @param jsonSerializer JSON 序列化器
	 */
	public SseResponse(HttpResponseStatus status, HttpHeaders headers, ConnectSession connectSession, JsonSerializer jsonSerializer) {
		super(HttpVersion.HTTP_1_1, status, headers);
		assert connectSession != null;
		this.connectSession = connectSession;
		this.setSseHeaders();
		this.jsonSerializer = jsonSerializer;
	}

	/**
	 * 设置 SSE 必需的 HTTP 头，包括：
	 * <ul>
	 *     <li>Content-Type: text/event-stream</li>
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
	 * @param sseCallback SSE 回调函数，接收当前 {@link ConnectSession}
	 */
	public void setSseCallback(Consumer<ConnectSession> sseCallback) {
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
	 * @param <T>          数据流中元素类型
	 */
	public <T> void setSseCallback(Flux<T> flux, Function<Throwable, String> errorHandler, Consumer<ConnectSession> onFinally) {
		Consumer<ConnectSession> consumer = (session) -> {
			flux.flatMap(res -> {
					try {
						if (res instanceof String s) {
							return Mono.just(s);
						} else {
							String json = jsonSerializer.beanToJson(res);
							return Mono.just(json);
						}
					} catch (Exception e) {
						return Mono.error(e);
					}
				})
				.doFinally(signalType -> {
					if (onFinally != null) {
						onFinally.accept(session);
					}
				})
				.subscribe(
					session::send,
					err -> {
						if (errorHandler != null) {
							String errorMessage = errorHandler.apply(err);
							session.send(errorMessage);
						} else {
							session.send("error:" + err.getMessage());
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
	 * @param <T>  数据流中元素类型
	 */
	public <T> void setSseCallback(Flux<T> flux) {
		this.setSseCallback(flux, null, ConnectSession::close);
	}

	@Override
	public InternalCallType getType() {
		return InternalCallType.SSE;
	}
}
