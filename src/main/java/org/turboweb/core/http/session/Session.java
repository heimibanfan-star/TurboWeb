package org.turboweb.core.http.session;

import java.util.Map;

/**
 * session接口
 */
public interface Session {

    /**
     * 设置session属性
     *
     * @param key   属性名
     * @param value 属性值
     */
    void setAttribute(String key, Object value);

    /**
     * 设置session属性
     *
     * @param key      属性名
     * @param value    属性值
     * @param timeout  超时时间
     */
    void setAttribute(String key, Object value, int timeout);

    /**
     * 移除session属性
     *
     * @param key 属性名
     */
    void removeAttribute(String key);

    /**
     * 获取session属性
     *
     * @param key 属性名
     * @return 属性值
     */
    Object getAttribute(String key);

    /**
     * 判断session是否过期
     *
     * @param maxNotUseTime 最大不适用时间
     * @return boolean true:过期 false:未过期
     */
    boolean isTimeout(long maxNotUseTime);

    /**
     * 获取所有session属性
     *
     * @return Map<String, SessionAttributeDefinition>
     */
    Map<String, SessionAttributeDefinition> getAllAttributeDefinitions();

    /**
     * 使用时设置时间
     */
    void setUseTime();

    /**
     * 设置session路径
     * @param path 路径
     */
    void setPath(String path);

    /**
     * 获取session路径
     * @return 路径
     */
    String getPath();
}
