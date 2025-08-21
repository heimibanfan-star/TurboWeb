package top.turboweb.http.session;

/**
 * session存储操作相关接口
 */
public interface HttpSessionStore {

    /**
     * 设置session属性
     * @param key 属性名
     * @param value 属性值
     */
    void setAttr(String key, Object value);

    /**
     * 获取session属性
     * @param key 属性名
     * @param value 属性值
     * @param timeout 属性过期时间
     */
    void setAttr(String key, Object value, long timeout);

    /**
     * 获取session属性
     * @param key 属性名
     * @return 属性值
     */
    Object getAttr(String key);

    /**
     * 获取session属性
     * @param key 属性名
     * @param clazz 属性值类型
     * @return 属性值
     */
    <T> T getAttr(String key, Class<T> clazz);

    /**
     * 删除session属性
     * @param key 属性名
     */
    void remAttr(String key);

    /**
     * 续时
     */
    void expireAt();
}
