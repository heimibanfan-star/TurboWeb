package top.turboweb.http.session;

/**
 * session管理器接口
 */
public interface SessionManager {

    /**
     * 设置session的内容
     *
     * @param sessionId sessionId
     * @param key session的key
     * @param value session的值
     */
    void setAttr(String sessionId, String key, Object value);

    /**
     * 设置session的内容
     *
     * @param sessionId sessionId
     * @param key session的key
     * @param value session的值
     * @param timeout session的过期时间
     */
    void setAttr(String sessionId, String key, Object value, long timeout);

    /**
     * 获取session的内容
     *
     * @param sessionId sessionId
     * @param key session的key
     * @return session的值
     */
    Object getAttr(String sessionId, String key);

    /**
     * 获取session的内容
     *
     * @param sessionId sessionId
     * @param key session的key
     * @param clazz session的值的class
     * @return session的值
     */
    <T> T getAttr(String sessionId, String key, Class<T> clazz);

    /**
     * 删除session的内容
     *
     * @param sessionId sessionId
     * @param key session的key
     */
    void remAttr(String sessionId, String key);

    /**
     * 判断session是否存在
     *
     * @param sessionId sessionId
     * @return session是否存在
     */
    boolean exist(String sessionId);

    /**
     * session垃圾回收
     *
     * @param checkTime 检查时间间隔
     * @param maxNotUseTime session最大不活跃时间
     * @param sessionNumThreshold session数量的检查阈值
     */
    void sessionGC(long checkTime, long maxNotUseTime, long sessionNumThreshold);

    /**
     * 获取session管理器的名称
     *
     * @return session管理器的名称
     */
    String sessionManagerName();
}
