package org.turbo.core.http.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.lock.Locks;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * session的容器
 */
public class SessionContainer {

    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(SessionContainer.class);

    /**
     * 启动session哨兵
     *
     * @param checkTime      检查间隔时间
     * @param maxNotUseTime  最大不活跃时间
     */
    public static void startSentinel(long checkTime, long maxNotUseTime) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            // 获取session的写锁
            Locks.SESSION_LOCK.writeLock().lock();
            try {
                log.debug("哨兵检查机制触发");
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
                log.debug("哨兵检查机制结束，当前时间：{}", LocalDateTime.now());
                Locks.SESSION_LOCK.writeLock().unlock();
            }
        }, checkTime, checkTime, TimeUnit.MILLISECONDS);
    }

    public static Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public static void addSession(String sessionId, Session session) {
        sessions.put(sessionId, session);
    }

    public static Map<String, Session> getAllSession() {
        return sessions;
    }
}
