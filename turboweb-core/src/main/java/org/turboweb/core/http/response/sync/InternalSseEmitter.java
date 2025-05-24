package org.turboweb.core.http.response.sync;

import org.turboweb.core.connect.ConnectSession;
import org.turboweb.core.connect.InternalConnectSession;

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
	public void initSse() {
		ReentrantReadWriteLock.WriteLock writeLock = sseLock.writeLock();
		try {
			writeLock.lock();
			// 发送SSE响应头
			InternalConnectSession internalConnectSession = (InternalConnectSession) session;
			internalConnectSession.getChannel().writeAndFlush(this);
			// 清空缓冲区
			while (!messageCache.isEmpty()) {
				String message = messageCache.poll();
				session.send(message);
			}
			isInit = true;
			messageCache = null;
		} finally {
			writeLock.unlock();
		}
	}
}
