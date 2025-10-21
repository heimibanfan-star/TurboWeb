package top.turboweb.http.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.lock.Locks;
import top.turboweb.commons.lock.SegmentLock;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于内存的会话管理器实现，适用于单机环境下的会话存储与管理。
 * <p>
 * 采用{@link ConcurrentHashMap}作为底层存储容器，支持高并发场景下的会话操作；
 * 通过分段锁（{@link SegmentLock}）减少会话创建时的并发冲突；内置定时垃圾回收机制，
 * 定期清理过期会话及属性，避免内存泄漏。该实现不适用于分布式环境，仅推荐在单机部署场景中使用。
 * </p>
 */
public class MemorySessionManager implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(MemorySessionManager.class);

    /**
     * 会话容器，键为sessionId，值为会话属性的映射对象（{@link MemorySessionMap}）。
     * 使用{@link ConcurrentHashMap}保证高并发场景下的线程安全。
     */
    private final Map<String, MemorySessionMap> sessionContainer = new ConcurrentHashMap<>();

    /**
     * 分段锁，用于会话创建时的并发控制。通过将不同sessionId映射到不同的锁段，
     * 减少锁竞争，提高并发性能。此处锁段数量为64。
     */
    private final SegmentLock segmentLock = new SegmentLock(64);

    /**
     * 用于标识垃圾回收线程是否已启动的原子布尔值，避免重复启动多个GC线程。
     * 采用{@link AtomicBoolean}保证线程间的可见性和操作的原子性。
     */
    private final AtomicBoolean isStartGC = new AtomicBoolean(false);

    /**
     * 为指定会话设置属性（无过期时间）。
     * <p>
     * 若会话不存在（即sessionId未在{@link #sessionContainer}中），则该操作无任何效果。
     * 属性值会被存储在对应的{@link MemorySessionMap}中，永久有效（直到会话过期或被删除）。
     * </p>
     *
     * @param sessionId 会话的唯一标识，非空字符串
     * @param key       属性的唯一标识，非空字符串
     * @param value     属性的值，可为null（此时等同于删除该属性）
     */
    @Override
    public void setAttr(String sessionId, String key, Object value) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        Optional.ofNullable(sessionMap).ifPresent(session -> sessionMap.setAttr(key, value));
    }

    /**
     * 为指定会话设置带过期时间的属性。
     * <p>
     * 若会话不存在，则该操作无任何效果。属性会在设置时刻起经过指定的过期时间后自动失效，
     * 失效后通过{@link #getAttr(String, String)}获取将返回null。
     * </p>
     *
     * @param sessionId 会话的唯一标识，非空字符串
     * @param key       属性的唯一标识，非空字符串
     * @param value     属性的值，可为null
     * @param timeout   属性的过期时间（单位：毫秒），若为负数则属性可能被立即清理
     */
    @Override
    public void setAttr(String sessionId, String key, Object value, long timeout) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        Optional.ofNullable(sessionMap).ifPresent(session -> sessionMap.setAttr(key, value, timeout));
    }

    /**
     * 从指定会话中获取属性值。
     * <p>
     * 若会话不存在或属性不存在/已过期，则返回null。该方法直接返回属性的原始值（Object类型），
     * 需手动进行类型转换。
     * </p>
     *
     * @param sessionId 会话的唯一标识，非空字符串
     * @param key       属性的唯一标识，非空字符串
     * @return 属性的值，若会话或属性无效则返回null
     */
    @Override
    public Object getAttr(String sessionId, String key) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        if (sessionMap == null) {
            return null;
        }
        return sessionMap.getAttr(key);
    }

    /**
     * 从指定会话中获取属性值，并转换为指定类型。
     * <p>
     * 若会话不存在、属性不存在/已过期或类型转换失败，则返回null。
     * 类型转换逻辑由{@link MemorySessionMap#getAttr(String, Class)}实现，
     * 支持基本数据类型及其包装类、String等常见类型的转换。
     * </p>
     *
     * @param sessionId 会话的唯一标识，非空字符串
     * @param key       属性的唯一标识，非空字符串
     * @param clazz     目标类型的Class对象，非空
     * @param <T>       目标类型的泛型参数
     * @return 转换后的属性值
     */
    @Override
    public <T> T getAttr(String sessionId, String key, Class<T> clazz) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        if (sessionMap == null) {
            return null;
        }
        return sessionMap.getAttr(key, clazz);
    }

    /**
     * 从指定会话中删除属性。
     * <p>
     * 若会话不存在，则该操作无任何效果。删除后，再次获取该属性将返回null。
     * </p>
     *
     * @param sessionId 会话的唯一标识，非空字符串
     * @param key       属性的唯一标识，非空字符串
     */
    @Override
    public void remAttr(String sessionId, String key) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        Optional.ofNullable(sessionMap).ifPresent(session -> sessionMap.remAttr(key));
    }

    /**
     * 判断指定会话是否存在。
     * <p>
     * 会话存在的判定标准为：sessionId在{@link #sessionContainer}中存在对应的{@link MemorySessionMap}实例，
     * 且该实例未被标记为过期（具体由{@link MemorySessionMap#isTimeout(long)}决定）。
     * </p>
     *
     * @param sessionId 会话的唯一标识，非空字符串
     * @return true：会话存在；false：会话不存在或已过期
     */
    @Override
    public boolean exist(String sessionId) {
        return sessionContainer.containsKey(sessionId);
    }

    /**
     * 创建会话映射对象（{@link MemorySessionMap}）。
     * <p>
     * 若会话已存在（sessionId已在容器中），则返回false；否则创建新的{@link MemorySessionMap}并添加到容器中，返回true。
     * 采用分段锁保证并发安全，避免同一sessionId被重复创建。
     * </p>
     *
     * @param sessionId 会话的唯一标识，非空字符串
     * @return true：创建成功；false：会话已存在，创建失败
     */
    @Override
    public boolean createSessionMap(String sessionId) {
        if (sessionContainer.containsKey(sessionId)) {
            return false;
        }
        // 尝试加锁
        segmentLock.lock(sessionId);
        try {
            if (sessionContainer.containsKey(sessionId)) {
                return false;
            }
            sessionContainer.put(sessionId, new MemorySessionMap());
            return true;
        } finally {
            segmentLock.unlock(sessionId);
        }
    }

    /**
     * 启动会话垃圾回收机制，定期清理过期会话及属性。
     * <p>
     * 该方法仅会启动一次GC线程（由{@link #isStartGC}控制），线程会按照指定的间隔时间执行以下操作：
     * 1. 当会话总数达到{@code sessionNumThreshold}时，触发清理；
     * 2. 遍历所有会话，移除已过期的会话（超过{@code maxNotUseTime}未使用）；
     * 3. 对未过期的会话，清理其内部已过期的属性。
     * 清理过程使用全局写锁（{@link Locks#SESSION_LOCK}）保证线程安全，避免并发修改冲突。
     * </p>
     *
     * @param checkTime          垃圾回收的检查间隔时间（单位：毫秒）
     * @param maxNotUseTime      会话的最大未使用时长（单位：毫秒），超过此时长的会话将被清理
     * @param checkForSessionNums 触发垃圾回收的会话数量阈值，低于该值时不执行清理
     */
    @Override
    public void sessionGC(long checkTime, long maxNotUseTime, long checkForSessionNums) {
        if (!isStartGC.compareAndSet(false, true)) {
            return;
        }
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {
            private final AtomicLong count = new AtomicLong(0);

            @Override
            public Thread newThread(Runnable r) {
                count.compareAndSet(Long.MAX_VALUE, 0);
                String threadName = "session-gc-thread-" + count.getAndIncrement();
                Thread thread = new Thread(r, threadName);
                thread.setDaemon(true);
                return thread;
            }
        });
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

    /**
     * 获取当前会话管理器的名称。
     * <p>
     * 用于日志输出和调试，标识当前使用的会话管理器类型。
     * </p>
     *
     * @return 固定返回"memory session manager"
     */
    @Override
    public String sessionManagerName() {
        return "memory session manager";
    }

    /**
     * 续时
     *
     * @param sessionId 会话id
     */
    @Override
    public void expireAt(String sessionId) {
        MemorySessionMap sessionMap = sessionContainer.get(sessionId);
        if (sessionMap != null) {
            sessionMap.expireAt();
        }
    }
}
