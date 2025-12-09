package top.turboweb.http.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的会话属性存储实现，实现{@link HttpSessionStore}接口。
 * <p>
 * 该类使用{@link ConcurrentHashMap}作为底层容器存储会话属性，支持属性级别的过期时间管理。
 * 每个属性通过{@link SessionAttributeDefinition}封装值和过期时间戳，提供属性过期判断及自动清理功能。
 * 同时维护会话的最后使用时间，支持会话级别的过期判断（基于最大不活跃时长）。
 * </p>
 * <p>
 * 线程安全说明：底层容器采用线程安全的{@link ConcurrentHashMap}，保证并发场景下的属性操作安全性；
 * 最后使用时间通过volatile修饰，确保多线程间的可见性。
 * </p>
 */
public class MemorySessionMap implements HttpSessionStore {

    /**
     * 会话属性的内部封装类，包含属性值和过期时间戳。
     * <p>
     * 提供{@link #isTimeout()}方法判断属性是否过期，过期时间戳为null表示属性永久有效。
     * </p>
     */
    private static class SessionAttributeDefinition {

        private final Object value;

        /**
         * 属性过期时间戳（毫秒级），null表示永久有效
         */
        private final Long timeoutTimestamp;

        /**
         * 构造属性定义实例
         *
         * @param value           属性值
         * @param timeoutTimestamp 过期时间戳（毫秒级），null表示永久有效
         */
        public SessionAttributeDefinition(Object value, Long timeoutTimestamp) {
            this.value = value;
            this.timeoutTimestamp = timeoutTimestamp;
        }

        /**
         * 获取属性值
         *
         * @return 属性值对象
         */
        public Object getValue() {
            return value;
        }

        /**
         * 判断属性是否过期
         *
         * @return true：已过期；false：未过期或永久有效
         */
        public boolean isTimeout() {
            if (timeoutTimestamp == null) {
                return false;
            }
            long timeMillis = System.currentTimeMillis();
            return timeMillis > timeoutTimestamp;
        }
    }


    /**
     * 会话最后一次被使用的时间戳（毫秒级），用于判断会话是否过期
     */
    private volatile long lastUseTime;

    /**
     * 存储会话属性的容器，键为属性名，值为属性定义对象
     */
    private final Map<String, SessionAttributeDefinition> sessionMap = new ConcurrentHashMap<>();

    /**
     * 构造方法，初始化会话最后使用时间为当前时间
     */
    public MemorySessionMap() {
        this.expireAt();
    }

    /**
     * 向会话中设置永久有效的属性
     *
     * @param key   属性的唯一标识
     * @param value 属性的值
     */
    @Override
    public void setAttr(String key, Object value) {
        sessionMap.put(key, new SessionAttributeDefinition(value, null));
    }

    /**
     * 向会话中设置带过期时间的属性
     *
     * @param key     属性的唯一标识
     * @param value   属性的值
     * @param timeout 过期时间（毫秒级），从当前时间开始计算
     */
    @Override
    public void setAttr(String key, Object value, long timeout) {
        timeout = System.currentTimeMillis() + timeout;
        sessionMap.put(key, new SessionAttributeDefinition(value, timeout));
    }

    /**
     * 从会话中获取属性值（自动过滤过期属性）
     *
     * @param key 属性的唯一标识
     * @return 属性值，若属性不存在或已过期则返回null
     */
    @Override
    public Object getAttr(String key) {
        // 获取session的定义信息
        SessionAttributeDefinition definition = sessionMap.get(key);
        // 判断session是否存在、是否过期
        if (definition == null || definition.isTimeout()) {
            return null;
        }
        // 获取正确的内容
        return definition.getValue();
    }

    /**
     * 从会话中获取属性值并转换为指定类型（自动过滤过期属性）
     *
     * @param key   属性的唯一标识
     * @param clazz 目标类型的Class对象
     * @return 转换后的属性值，若属性不存在、已过期则返回null
     */
    @Override
    public <T> T getAttr(String key, Class<T> clazz) {
        Object value = getAttr(key);
        if (value == null) {
            return null;
        }
        return clazz.cast(value);
    }

    /**
     * 从会话中删除指定属性
     *
     * @param key 属性的唯一标识
     */
    @Override
    public void remAttr(String key) {
        sessionMap.remove(key);
    }

    /**
     * 清理会话中所有已过期的属性
     */
    public void timeoutValGC() {
        sessionMap.entrySet()
                .removeIf(entry -> entry.getValue().isTimeout());
    }

    /**
     * 将会话最后使用时间更新为当前时间（续期操作）
     */
    @Override
    public void expireAt() {
        this.lastUseTime = System.currentTimeMillis();
    }

    /**
     * 判断会话是否过期（基于最大不活跃时长）
     *
     * @param maxNotUseTime 最大不活跃时长（毫秒级）
     * @return true：已过期；false：未过期
     */
    public boolean isTimeout(long maxNotUseTime) {
        long timeMillis = System.currentTimeMillis();
        return timeMillis - lastUseTime > maxNotUseTime;
    }
}
