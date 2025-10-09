package top.turboweb.http.response;

import io.netty.handler.codec.http.*;
import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.http.connect.ConnectSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * sse的响应结果
 */
public class SseResponse extends DefaultHttpResponse implements InternalCallResponse {

	private final ConnectSession connectSession;
	private Consumer<ConnectSession> sseCallback;
	private final JsonSerializer jsonSerializer;

	public SseResponse(HttpResponseStatus status, HttpHeaders headers, ConnectSession connectSession, JsonSerializer jsonSerializer) {
		super(HttpVersion.HTTP_1_1, status, headers);
		assert connectSession != null;
		this.connectSession = connectSession;
		this.setSseHeaders();
		this.jsonSerializer = jsonSerializer;
	}

	private void setSseHeaders() {
		this.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
		this.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
		this.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
		this.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
		HttpUtil.setTransferEncodingChunked(this, true); // 开启 Chunked 传输
	}

	public void setSseCallback(Consumer<ConnectSession> sseCallback) {
		this.sseCallback = sseCallback;
	}

	/**
	 * 开始sse(框架会自行调用，请勿手动调用)
	 */
	public void startSse() {
		if (sseCallback != null) {
			sseCallback.accept(connectSession);
		}
	}

	/**
	 * 设置sse的回调函数
	 *
	 * @param flux          sse的响应数据
	 * @param errorHandler  sse的错误处理函数
	 * @param <T>           sse的响应数据类型
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
	 * 设置sse的回调函数
	 *
	 * @param flux          sse的响应数据
	 * @param <T>           sse的响应数据类型
	 */
	public <T> void setSseCallback(Flux<T> flux) {
		this.setSseCallback(flux, null, ConnectSession::close);
	}

	@Override
	public InternalCallType getType() {
		return InternalCallType.SSE;
	}
}
