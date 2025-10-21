package top.turboweb.http.response;

import io.netty.handler.codec.http.*;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.commons.exception.TurboSseException;

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * SSE (Server-Sent Events) 发射器抽象类。
 * <p>
 * 用于向客户端推送事件流，支持消息缓存、异步发送和连接管理。
 * 实现了 {@link InternalCallResponse} 接口，类型为 {@link InternalCallResponse.InternalCallType#SSE}。
 * <p>
 * 特点：
 * <ul>
 *     <li>支持消息缓存，当连接未初始化时存储消息</li>
 *     <li>支持异步发送消息到客户端</li>
 *     <li>支持连接关闭及回调处理</li>
 * </ul>
 */
public abstract class SseEmitter extends DefaultHttpResponse implements InternalCallResponse {

	/**
	 * 关联的连接会话
	 */
	protected final ConnectSession session;

	/**
	 * 是否已初始化
	 */
	protected volatile boolean isInit = false;

	/**
	 * 消息缓存队列
	 */
	protected LinkedList<String> messageCache = new LinkedList<>();

	/**
	 * 消息缓存最大数量
	 */
	private final int maxMessageCache;

	/**
	 * 当前消息缓存的数量
	 */
	protected int messageCacheSize = 0;

	/**
	 * 缓存操作锁
	 */
	protected ReentrantLock cacheLock = new ReentrantLock();

	/**
	 * SSE 读写锁，保证发送和初始化的线程安全
	 */
	protected final ReentrantReadWriteLock sseLock = new ReentrantReadWriteLock(true);

	/**
	 * 构造 SSE 发射器。
	 *
	 * @param session         关联的连接会话
	 * @param maxMessageCache 消息缓存最大数量
	 */
	public SseEmitter(ConnectSession session, int maxMessageCache) {
		super(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		this.session = session;
		this.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
		this.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
		this.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
		this.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
		HttpUtil.setTransferEncodingChunked(this, true); // 开启 Chunked 传输
		this.maxMessageCache = maxMessageCache;
	}

	/**
	 * 发送消息到客户端。
	 * <p>
	 * 如果 SSE 未初始化，则将消息保存到缓存；初始化后直接发送。
	 *
	 * @param message 消息内容
	 */
	public void send(String message) {
		ReentrantReadWriteLock.ReadLock readLock = sseLock.readLock();
		try {
			readLock.lock();
			if (!isInit) {
				saveMessageToCache(message);
			} else {
				session.send(message);
			}
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * 保存消息到缓存。
	 *
	 * @param message 消息内容
	 * @throws TurboSseException 当缓存已满时抛出异常
	 */
	private void saveMessageToCache(String message) {
		cacheLock.lock();
		try {
			if (messageCacheSize >= maxMessageCache) {
				throw new TurboSseException("消息缓存已满");
			}
			// 放入消息
			messageCache.add(message);
			messageCacheSize++;
		} finally {
			cacheLock.unlock();
		}
	}

	/**
	 * 关闭连接
	 */
	public void close() {
		session.close();
	}

	/**
	 * 设置 SSE 连接关闭回调。
	 *
	 * @param consumer 关闭时的回调函数
	 */
	public void onClose(Consumer<SseEmitter> consumer) {
		session.closeListener(() -> {
			consumer.accept(this);
		});
	}

	/**
	 * 获取内部调用类型。
	 *
	 * @return {@link InternalCallResponse.InternalCallType#SSE}
	 */
	@Override
	public InternalCallType getType() {
		return InternalCallType.SSE;
	}
}
