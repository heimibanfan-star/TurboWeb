package org.example.session;

import top.turboweb.commons.lock.Locks;
import top.turboweb.http.session.SessionManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MySessionManager implements SessionManager {
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
            // 获取锁
            Locks.SESSION_LOCK.writeLock();
            try {
                // 在当前临界区中所有的请求线程会被阻塞住
            } finally {
                Locks.SESSION_LOCK.writeLock().unlock();
                // 请求线程恢复运行
            }
        }, checkTime, checkTime, TimeUnit.MILLISECONDS);
    }

    @Override
    public String sessionManagerName() {
        return "";
    }

    @Override
    public void expireAt(String sessionId) {

    }
}
