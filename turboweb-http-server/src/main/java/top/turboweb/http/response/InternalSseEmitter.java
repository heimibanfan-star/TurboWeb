package top.turboweb.http.response;

import io.netty.channel.ChannelFuture;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.connect.InternalConnectSession;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 框架内部使用的 SSE 发射器。
 * <p>
 * 继承 {@link SseEmitter}，用于框架内部初始化和管理 SSE 连接。
 * 禁止开发者直接调用 {@link #initSse()}，由框架内部调用。
 */
public class InternalSseEmitter extends SseEmitter{

	/**
	 * 构造内部 SSE 发射器。
	 *
	 * @param session         关联的连接会话
	 * @param maxMessageCache 消息缓存最大数量
	 */
	public InternalSseEmitter(ConnectSession session, int maxMessageCache) {
		super(session, maxMessageCache);
	}

	/**
	 * 初始化 SSE 发射器。
	 * <p>
	 * 发送 SSE 响应头，清空缓存消息，并标记 SSE 已初始化。
	 * 由框架内部调用，禁止开发者直接使用。
	 *
	 * @return {@link ChannelFuture} 当前写入操作的异步结果
	 */
	public ChannelFuture initSse() {
		ReentrantReadWriteLock.WriteLock writeLock = sseLock.writeLock();
		ChannelFuture channelFuture = null;
		try {
			writeLock.lock();
			// 发送SSE响应头
			InternalConnectSession internalConnectSession = (InternalConnectSession) session;
			channelFuture = internalConnectSession.getChannel().writeAndFlush(this);
			// 清空缓冲区
			cacheLock.lock();
			try {
				while (!messageCache.isEmpty()) {
					String message = messageCache.poll();
					channelFuture = session.send(message);
				}
			} finally {
				cacheLock.unlock();
			}
			isInit = true;
			// 卸载缓存
			messageCache = null;
			cacheLock = null;
		} finally {
			writeLock.unlock();
		}
		return channelFuture;
	}
}
