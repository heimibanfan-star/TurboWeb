package top.turboweb.http.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.lock.Locks;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 呢哦村session管理器
 */
public class MemorySessionManager implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(MemorySessionManager.class);
    private final Map<String, MemorySessionMap> sessionContainer = new ConcurrentHashMap<>();

    @Override
    public void setAttr(String sessionId, String key, Object value) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        Optional.ofNullable(sessionMap).ifPresent(session -> sessionMap.setAttr(key, value));
    }

    @Override
    public void setAttr(String sessionId, String key, Object value, long timeout) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        Optional.ofNullable(sessionMap).ifPresent(session -> sessionMap.setAttr(key, value, timeout));
    }

    @Override
    public Object getAttr(String sessionId, String key) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        if (sessionMap == null) {
            return null;
        }
        return sessionMap.getAttr(key);
    }

    @Override
    public <T> T getAttr(String sessionId, String key, Class<T> clazz) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        if (sessionMap == null) {
            return null;
        }
        return sessionMap.getAttr(key, clazz);
    }

    @Override
    public void remAttr(String sessionId, String key) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        Optional.ofNullable(sessionMap).ifPresent(session -> sessionMap.remAttr(key));
    }

    @Override
    public boolean exist(String sessionId) {
        return sessionContainer.containsKey(sessionId);
    }

    @Override
    public boolean createSessionMap(String sessionId) {
        if (sessionContainer.containsKey(sessionId)) {
            return false;
        }
        synchronized (sessionId.intern()) {
            if (sessionContainer.containsKey(sessionId)) {
                return false;
            }
            sessionContainer.put(sessionId, new MemorySessionMap());
            return true;
        }
    }

    @Override
    public void sessionGC(long checkTime, long maxNotUseTime, long checkForSessionNums) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            // 判断是否到达检查条件
            if (sessionContainer.size() < checkForSessionNums) {
                return;
            }
            long start = System.currentTimeMillis();
            // 获取session的写锁
            Locks.SESSION_LOCK.writeLock().lock();
            try {
                log.debug("session垃圾回收器触发");
                Iterator<Map.Entry<String, MemorySessionMap>> iterator = sessionContainer.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, MemorySessionMap> entry = iterator.next();
                    MemorySessionMap sessionMap = entry.getValue();
                    if (sessionMap.isTimeout(maxNotUseTime)) {
                        iterator.remove();
                    } else {
                        sessionMap.timeoutValGC();
                    }
                }
            } finally {
                Locks.SESSION_LOCK.writeLock().unlock();
                long end = System.currentTimeMillis();
                log.info("session垃圾回收器检查结束，耗时：{}ms", end - start);
            }
        }, checkTime, checkTime, TimeUnit.MILLISECONDS);
    }

    @Override
    public String sessionManagerName() {
        return "memory session manager";
    }

    @Override
    public void expireAt(String sessionId) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        if (sessionMap != null) {
            sessionMap.expireAt();
        }
    }
}
