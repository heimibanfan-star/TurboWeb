package org.turbo.web.core.http.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.lock.Locks;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 呢哦村session管理器
 */
public class MemorySessionManager implements SessionManager {

    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(SessionManagerProxy.class);

    @Override
    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public void addSession(String sessionId, Session session) {
        sessions.put(sessionId, session);
    }

    @Override
    public Map<String, Session> getAllSession() {
        return sessions;
    }

    @Override
    public void startSessionGC(long checkTime, long maxNotUseTime, long checkForSessionNums) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            // 判断是否到达检查条件
            if (sessions.size() < checkForSessionNums) {
                return;
            }
            long start = System.currentTimeMillis();
            // 获取session的写锁
            Locks.SESSION_LOCK.writeLock().lock();
            try {
                log.debug("session垃圾回收器触发");
                Iterator<Map.Entry<String, Session>> iterator = sessions.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Session> entry = iterator.next();
                    Session session = entry.getValue();
                    if (session.isTimeout(maxNotUseTime)) {
                        iterator.remove();
                        log.debug("释放长时间不用的session:{}", session);
                    }
                    // 判断里面的key是否过期
                    Map<String, SessionAttributeDefinition> attributeDefinitions = session.getAllAttributeDefinitions();
                    Iterator<Map.Entry<String, SessionAttributeDefinition>> entryIterator = attributeDefinitions.entrySet().iterator();
                    while (entryIterator.hasNext()) {
                        Map.Entry<String, SessionAttributeDefinition> attributeDefinitionEntry = entryIterator.next();
                        SessionAttributeDefinition value = attributeDefinitionEntry.getValue();
                        if (value.isTimeout()) {
                            entryIterator.remove();
                            log.debug("释放过期的key：{}", attributeDefinitionEntry.getKey());
                        }
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
    public String getSessionManagerName() {
        return "MemorySessionManager";
    }
}
