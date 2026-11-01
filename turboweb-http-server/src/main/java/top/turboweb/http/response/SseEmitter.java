package top.turboweb.http.response;

import reactor.core.publisher.Sinks;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.core.StatusLine;
import top.turboweb.http.exception.SseBufferException;
import top.turboweb.http.orchestrator.writer.MessageWriteHandler;
import top.turboweb.http.orchestrator.writer.SseMessageWriteHandler;
import top.turboweb.http.orchestrator.writer.WriteHandlerSubscribe;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE（Server-Sent Events）发射器：以响应消息形态持有事件流源，
 * 事件经内部的 {@link Sinks.Many} 异步投递，由内置 SSE 写出处理器
 * {@link SseMessageWriteHandler} 在写出阶段订阅并逐块推送到客户端。
 * <p>
 * 发射器不持有连接：写出处理器订阅流后，结束信号（{@link #complete()}）与流错误
 * 都由写出处理器以关闭通道收尾，通道关闭事件再触发 {@link #addCloseListener(Runnable)}
 * 登记的关闭监听器，因此连接的关闭与断开均经监听器对外通知。
 * </p>
 * <p>
 * 默认响应头携带 {@code Content-Type: text/event-stream}、{@code Cache-Control: no-cache}
 * 与 {@code Connection: keep-alive}；事件字节编码由写出处理器按 Content-Type 的
 * charset 参数确定，未携带或非法时降级 UTF-8。
 * </p>
 */
public final class SseEmitter extends HttpResponseMessage<Sinks.Many<String>> implements WriteHandlerSubscribe {

	/** 默认事件缓冲容量 */
	public static final int DEFAULT_BUFFER_CAPACITY = 32;

	/**
	 * 关闭监听器表：由写出处理器注册到通道关闭事件上，通道关闭时任一时刻登记的监听器都逐个触发
	 */
	private final List<Runnable> closeListeners = new CopyOnWriteArrayList<>();

	/**
	 * 以默认缓冲容量建立 SSE 发射器。
	 */
	public SseEmitter() {
		this(DEFAULT_BUFFER_CAPACITY);
	}

	/**
	 * 以指定缓冲容量建立 SSE 发射器。
	 * <p>
	 * 缓冲容量限制写出处理器订阅前可暂存的事件数，写出开始后随消费推进循环复用。
	 * </p>
	 *
	 * @param bufferCapacity 事件缓冲容量
	 * @throws IllegalArgumentException 当容量非正数时抛出
	 */
	public SseEmitter(int bufferCapacity) {
		super(StatusLine.OK, newSink(bufferCapacity));
		headers().set("Content-Type", "text/event-stream");
		headers().set("Cache-Control", "no-cache");
		headers().set("Connection", "keep-alive");
	}

	/**
	 * 建立容量受限的单播事件源：写出前的事件在容量受限的队列中缓冲，队列满时投递失败。
	 *
	 * @param bufferCapacity 事件缓冲容量，须为正数
	 * @return 单播事件源
	 * @throws IllegalArgumentException 当容量非正数时抛出
	 */
	private static Sinks.Many<String> newSink(int bufferCapacity) {
		if (bufferCapacity <= 0) {
			throw new IllegalArgumentException("bufferCapacity must be positive: " + bufferCapacity);
		}
		return Sinks.many().unicast().onBackpressureBuffer(new ArrayBlockingQueue<>(bufferCapacity));
	}

	/**
	 * 投递一条事件到流上。
	 * <p>
	 * 写出处理器订阅前事件进入缓冲；缓冲已满时以 {@link SseBufferException} 报告，
	 * 发射器结束后投递以 {@link IllegalStateException} 报告。可多线程并发投递。
	 * </p>
	 *
	 * @param message 事件原文，按原样投递
	 * @throws SseBufferException    当缓冲已满时抛出
	 * @throws IllegalStateException 当发射器已结束后投递时抛出
	 */
	public void send(String message) {
		Objects.requireNonNull(message, "message must not be null");
		content().emitNext(message, (signalType, result) -> {
			throw toException(result);
		});
	}

	/**
	 * 投递一条 data 字段事件，等同投递 {@code "data:" + data + "\n\n"}。
	 *
	 * @param data 数据内容
	 */
	public void sendData(String data) {
		send("data:" + data + "\n\n");
	}

	/**
	 * 投递一条 event 字段事件，等同投递 {@code "event:" + event + "\n\n"}。
	 *
	 * @param event 事件内容
	 */
	public void sendEvent(String event) {
		send("event:" + event + "\n\n");
	}

	/**
	 * 投递一条 id 字段事件，等同投递 {@code "id:" + id + "\n\n"}。
	 *
	 * @param id ID 内容
	 */
	public void sendId(String id) {
		send("id:" + id + "\n\n");
	}

	/**
	 * 投递一条 retry 字段事件，等同投递 {@code "retry:" + retry + "\n\n"}。
	 *
	 * @param retry 重试间隔（毫秒）
	 */
	public void sendRetry(int retry) {
		send("retry:" + retry + "\n\n");
	}

	/**
	 * 投递一条注释事件，等同投递 {@code ":" + comment + "\n\n"}。
	 *
	 * @param comment 注释内容
	 */
	public void sendComment(String comment) {
		send(":" + comment + "\n\n");
	}

	/**
	 * 发出结束信号：流正常终止，写出处理器随后关闭通道。
	 *
	 * @throws IllegalStateException 当发射器已结束后再次结束时抛出
	 */
	public void complete() {
		content().emitComplete((signalType, result) -> {
			throw toException(result);
		});
	}


	/**
	 * 登记关闭监听器：通道关闭（写出处理器收到结束信号后主动关闭，或客户端断开）时触发。
	 * <p>监听器在通道关闭的触发线程上运行，宜保持轻量。</p>
	 *
	 * @param listener 监听器
	 */
	public void addCloseListener(Runnable listener) {
		closeListeners.add(Objects.requireNonNull(listener, "listener must not be null"));
	}

	/**
	 * 获取已登记的关闭监听器，供写出处理器注册到通道关闭事件上。
	 *
	 * @return 监听器只读视图，登记后立即可见
	 */
	public List<Runnable> closeListeners() {
		return Collections.unmodifiableList(closeListeners);
	}

	/**
	 * 把投递失败结果转为对应异常：缓冲满（写出版未订阅亦计入缓冲满）转缓冲异常，
	 * 已结束转状态异常。
	 *
	 * @param result 投递失败结果
	 * @return 待抛出的异常
	 */
	private static RuntimeException toException(Sinks.EmitResult result) {
		if (result == Sinks.EmitResult.FAIL_OVERFLOW || result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
			return new SseBufferException("The message cache is full");
		}
		if (result == Sinks.EmitResult.FAIL_TERMINATED) {
			return new IllegalStateException("The sse emitter has already completed");
		}
		return new IllegalStateException("Failed to emit sse message: " + result);
	}

	/**
	 * 声明本消息由内置 SSE 写出处理器写出。
	 *
	 * @return 内置 SSE 写出处理器的类型
	 */
	@Override
	public Class<? extends MessageWriteHandler> subscriber() {
		return SseMessageWriteHandler.class;
	}
}
