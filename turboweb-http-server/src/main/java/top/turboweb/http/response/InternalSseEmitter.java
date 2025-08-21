package top.turboweb.http.response;

import io.netty.channel.ChannelFuture;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.connect.InternalConnectSession;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * sse发射器
 */
public class InternalSseEmitter extends SseEmitter{
	public InternalSseEmitter(ConnectSession session, int maxMessageCache) {
		super(session, maxMessageCache);
	}

	/**
	 * 初始化SSE发射器（由框架内部初始化，禁止开发者调用）
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
