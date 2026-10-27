package top.turboweb.http.context.attchment;

/**
 * 上下文中数据的挂载部分
 */
public interface Attachment {

    /**
     * 挂载对象
     * @param obj 挂载对象
     */
    void attach(Object obj);

    /**
     * 挂载对象
     * @param key 挂载对象对应的key
     * @param obj 挂载对象
     */
    void attach(String key, Object obj);

    /**
     * 获取挂载对象
     * @param key 挂载对象对应的key
     * @return 挂载对象
     */
    Object getAttachment(String key);

    /**
     * 获取挂载对象
     * @param key 挂载对象对应的key
     * @param clazz 挂载对象对应的class
     * @return 挂载对象
     */
    <T> T getAttachment(String key, Class<T> clazz);

    /**
     * 获取挂载对象
     * @param clazz 挂载对象对应的class
     * @return 挂载对象
     */
    <T> T getAttachment(Class<T> clazz);
}
