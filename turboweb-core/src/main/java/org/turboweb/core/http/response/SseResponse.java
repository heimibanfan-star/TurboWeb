package org.turboweb.core.http.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.*;
import org.turboweb.core.connect.ConnectSession;
import org.turboweb.commons.utils.base.BeanUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * sse的响应结果
 */
public class SseResponse extends DefaultHttpResponse {

	private final ConnectSession connectSession;
	private Consumer<ConnectSession> sseCallback;

	public SseResponse(HttpVersion version, HttpResponseStatus status, HttpHeaders headers, ConnectSession connectSession) {
		super(version, status, headers);
		assert connectSession != null;
		this.connectSession = connectSession;
		this.setSseHeaders();
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
							String json = BeanUtils.getObjectMapper().writeValueAsString(res);
							return Mono.just(json);
						}
					} catch (JsonProcessingException e) {
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
}
