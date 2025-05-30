package top.turboweb.http.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储session
 */
public class MemorySessionMap implements HttpSessionStore {

    private static class SessionAttributeDefinition {

        private final Object value;

        // 过期时间
        private final Long timeoutTimestamp;

        public SessionAttributeDefinition(Object value, Long timeoutTimestamp) {
            this.value = value;
            this.timeoutTimestamp = timeoutTimestamp;
        }

        public Object getValue() {
            return value;
        }

        /**
         * 判断是否过期
         *
         * @return 是否过期
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
     * 最后一次使用的时间
     */
    private volatile long lastUseTime;
    private final Map<String, SessionAttributeDefinition> sessionMap = new ConcurrentHashMap<>();

    public MemorySessionMap() {
        this.expireAt();
    }

    /**
     * 设置session
     *
     * @param key   session的key
     * @param value session的值
     */
    @Override
    public void setAttr(String key, Object value) {
        this.expireAt();
        sessionMap.put(key, new SessionAttributeDefinition(value, null));
    }

    /**
     * 设置session
     *
     * @param key   session的key
     * @param value session的值
     * @param timeout 过期时间
     */
    @Override
    public void setAttr(String key, Object value, long timeout) {
        this.expireAt();
        timeout = System.currentTimeMillis() + timeout;
        sessionMap.put(key, new SessionAttributeDefinition(value, timeout));
    }

    /**
     * 获取session
     *
     * @param key session的key
     * @return session的值
     */
    @Override
    public Object getAttr(String key) {
        this.expireAt();
        // 获取session的定义信息
        SessionAttributeDefinition definition = sessionMap.get(key);
        // 判断session是否存在、是否过期
        if (definition == null) {
            return null;
        }
        if (definition.isTimeout()) {
            sessionMap.remove(key);
            return null;
        }
        // 获取正确的内容
        return definition.getValue();
    }

    /**
     * 获取session
     *
     * @param key session的key
     * @param clazz session的值的类型
     * @return session的值
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
     * 删除session
     *
     * @param key session的key
     */
    @Override
    public void remAttr(String key) {
        this.expireAt();
        sessionMap.remove(key);
    }

    public void timeoutValGC() {
        sessionMap.entrySet()
                .removeIf(entry -> entry.getValue().isTimeout());
    }

    /**
     * 续时
     *
     */
    public void expireAt() {
        this.lastUseTime = System.currentTimeMillis();
    }

    /**
     * 判断session是否过期
     *
     * @param maxNotUseTime 最大不活跃时间
     * @return 是否过期
     */
    public boolean isTimeout(long maxNotUseTime) {
        long timeMillis = System.currentTimeMillis();
        return timeMillis - lastUseTime > maxNotUseTime;
    }
}
