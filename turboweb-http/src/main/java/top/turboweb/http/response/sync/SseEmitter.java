package top.turboweb.http.response.sync;

import io.netty.handler.codec.http.*;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.commons.exception.TurboSseException;

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * sse发射器
 */
public abstract class SseEmitter extends DefaultHttpResponse {

	protected final ConnectSession session;
	protected volatile boolean isInit = false;
	protected LinkedList<String> messageCache = new LinkedList<>();
	private final int maxMessageCache;
	protected int messageCacheSize = 0;
	protected ReentrantLock cacheLock = new ReentrantLock();
	protected final ReentrantReadWriteLock sseLock = new ReentrantReadWriteLock();

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
	 * 发送消息
	 *
	 * @param message 消息
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
	 * 保存消息到缓存
	 *
	 * @param message 消息
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
	 * 关闭连接
	 *
	 * @param consumer 关闭回调
	 */
	public void onClose(Consumer<SseEmitter> consumer) {
		session.closeListener(() -> {
			consumer.accept(this);
		});
	}
}
