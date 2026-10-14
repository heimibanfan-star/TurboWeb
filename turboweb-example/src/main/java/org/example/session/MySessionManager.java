package org.example.session;

import top.turboweb.http.core.session.SessionManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class MySessionManager implements SessionManager {

    /**
     * 本管理器实例的读写互斥锁：GC 清扫持写侧，请求路径经 sessionSafeScope 持读侧
     */
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

    @Override
    public void setAttr(String sessionId, String key, Object value) {

    }

    @Override
    public void setAttr(String sessionId, String key, Object value, long timeout) {

    }

    @Override
    public Object getAttr(String sessionId, String key) {
        return null;
    }

    @Override
    public <T> T getAttr(String sessionId, String key, Class<T> clazz) {
        return null;
    }

    @Override
    public void remAttr(String sessionId, String key) {

    }

    @Override
    public boolean exist(String sessionId) {
        return false;
    }

    @Override
    public boolean createSessionMap(String sessionId) {
        return false;
    }

    @Override
    public void sessionGC(long checkTime, long maxNotUseTime, long sessionNumThreshold) {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleWithFixedDelay(() -> {
            // 获取写锁
            ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                // 在当前临界区中所有的请求线程会被阻塞住
            } finally {
                writeLock.unlock();
                // 请求线程恢复运行
            }
        }, checkTime, checkTime, TimeUnit.MILLISECONDS);
    }

    @Override
    public <T> T sessionSafeScope(Function<SessionManager, T> action) {
        ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return action.apply(this);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public String sessionManagerName() {
        return "";
    }

    @Override
    public void expireAt(String sessionId) {

    }
}
