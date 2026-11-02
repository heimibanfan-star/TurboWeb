package top.turboweb.http.orchestrator.writer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.http.connection.HttpConnection;
import top.turboweb.http.connection.SequencedHttpConnection;
import top.turboweb.http.core.ContentType;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.server.runtime.ChannelAttributeKeys;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * SSE 响应消息写出处理器：把 {@link SseEmitter} 持有的事件流逐块写出到连接上，
 * 并在收到结束信号或流错误后关闭通道，通道关闭事件再触发发射器上登记的关闭监听器。
 * <p>
 * 写出按响应首部、逐块事件的次序推进：首部写出完成后才订阅流并发出首块，
 * 流每产出一块即经连接写出，上一块的写出凭据完成后才发出下一块（在途窗口为 1），
 * 背压由此闭合。首部未携带传输编码头时补齐 chunked 分块传输声明，已携带时不覆写；
 * 事件流长度由通道关闭划定，已携带的 Content-Length 头在写出前剔除。
 * </p>
 * <p>
 * 事件字节编码取 Content-Type 头的 charset 参数，未携带、无法解析或字符集不存在时降级 UTF-8。
 * SSE 属长生命周期连接：启动即落空闲豁免标记，并把发射器的关闭监听器聚合注册到通道关闭事件上；
 * 结束信号、流错误与写出失败均以关闭通道收尾，不写结束标记。写出结果经回调以
 * {@link CallbackType#IGNORE} 恰好上报一次，后续失败不重复上报，
 * 经由通道关闭与关闭监听器对外暴露。
 * </p>
 */
public final class SseMessageWriteHandler implements MessageWriteHandler {

	/** 传输编码头名 */
	private static final String TRANSFER_ENCODING = "Transfer-Encoding";
	/** 分块传输编码取值 */
	private static final String CHUNKED = "chunked";
	/** 消息体长度头名：与分块传输互斥，长度由通道关闭划定 */
	private static final String CONTENT_LENGTH = "Content-Length";
	/** 内容类型头名 */
	private static final String CONTENT_TYPE = "Content-Type";

	@Override
	public void write(HttpConnection connection, HttpResponseMessage<?> message, Callback callback) {
		if (!(message instanceof SseEmitter sseEmitter)) {
			throw new IllegalArgumentException("Invalid message type: " + message.getClass().getName());
		}
		// 关闭监听以聚合钩子注册：通道关闭时任一时刻登记的监听器都逐个触发
		connection.closeListener(() -> {
			for (Runnable listener : sseEmitter.closeListeners()) {
				listener.run();
			}
		});
		if (!sseEmitter.headers().contains(TRANSFER_ENCODING)) {
			sseEmitter.headers().set(TRANSFER_ENCODING, CHUNKED);
		}
		sseEmitter.headers().remove(CONTENT_LENGTH);
		Charset charset = resolveCharset(sseEmitter.headers().get(CONTENT_TYPE));
		// SSE 启动即移交长生命周期协议：先落空闲豁免标记，再注册关闭监听与订阅流出
		if (connection instanceof SequencedHttpConnection internalConnection) {
			internalConnection.getChannel().attr(ChannelAttributeKeys.IDLE_EXEMPT).set(Boolean.TRUE);
		}
		Flux<byte[]> events = sseEmitter.content().asFlux().map(event -> event.getBytes(charset));
		try {
			Mono.fromFuture(() -> connection.writeHeaders(sseEmitter.statusLine(), sseEmitter.headers().asMap()))
					.then(events
							.concatMap(chunk -> Mono.fromFuture(connection.write(chunk)), 1)
							.then())
					.subscribe(null,
							// 流结束或写出失败均以关闭通道收尾，关闭事件沿通道关闭链路通知关闭监听器
							error -> connection.close(),
							connection::close);
		} finally {
			callback.onFinally(null, CallbackType.IGNORE);
		}
	}

	/**
	 * 从 Content-Type 取值中解析 charset 参数：解析委托给 {@link ContentType#parse}，
	 * 参数名忽略大小写、取值容忍引号包裹与转义、参数名与等号周围空白不计入匹配、
	 * 逗号分隔多元素时取首元素；未携带、无法解析、charset 缺省或
	 * charset 取值非法与字符集不受支持时降级 UTF-8。
	 *
	 * @param contentType Content-Type 头取值，可为 {@code null}
	 * @return 事件字节编码
	 */
	private static Charset resolveCharset(String contentType) {
		try {
			ContentType parsed = ContentType.parse(contentType);
			if (parsed != null && parsed.getCharset() != null) {
				return parsed.getCharset();
			}
		} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
			// charset 取值非法或当前 JVM 不支持：降级 UTF-8
		}
		return StandardCharsets.UTF_8;
	}
}
