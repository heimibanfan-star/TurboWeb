package top.turboweb.http.session;

/**
 * 会话管理器核心接口，定义会话的创建、属性操作、过期管理及垃圾回收等功能。
 * <p>
 * 该接口为各类会话存储实现（如内存、数据库、分布式缓存等）提供统一规范，
 * 支持会话级别的属性CRUD操作，并内置会话过期检测与自动清理机制。
 * </p>
 * <p>
 * 实现类需保证线程安全，支持高并发场景下的会话操作；同时需实现会话垃圾回收逻辑，
 * 定期清理过期会话及属性，避免资源泄漏。
 * </p>
 */
public interface SessionManager {

    /**
     * 为指定会话设置永久有效的属性
     *
     * @param sessionId 会话的唯一标识（非空）
     * @param key       属性的唯一标识（非空）
     * @param value     属性的值（可为null，此时等同于删除该属性）
     */
    void setAttr(String sessionId, String key, Object value);

    /**
     * 为指定会话设置带过期时间的属性
     *
     * @param sessionId 会话的唯一标识（非空）
     * @param key       属性的唯一标识（非空）
     * @param value     属性的值（可为null）
     * @param timeout   属性的过期时间（毫秒级），从当前时间开始计算
     */
    void setAttr(String sessionId, String key, Object value, long timeout);

    /**
     * 从指定会话中获取属性值
     *
     * @param sessionId 会话的唯一标识（非空）
     * @param key       属性的唯一标识（非空）
     * @return 属性值，若会话不存在、属性不存在或已过期则返回null
     */
    Object getAttr(String sessionId, String key);

    /**
     * 从指定会话中获取属性值并转换为指定类型
     *
     * @param sessionId 会话的唯一标识（非空）
     * @param key       属性的唯一标识（非空）
     * @param clazz     目标类型的Class对象（非空）
     * @param <T>       目标类型的泛型参数
     * @return 转换后的属性值，若会话不存在、属性无效则返回null
     */
    <T> T getAttr(String sessionId, String key, Class<T> clazz);

    /**
     * 从指定会话中删除属性
     *
     * @param sessionId 会话的唯一标识（非空）
     * @param key       属性的唯一标识（非空）
     */
    void remAttr(String sessionId, String key);

    /**
     * 判断指定会话是否存在（未过期）
     *
     * @param sessionId 会话的唯一标识（非空）
     * @return true：会话存在；false：会话不存在或已过期
     */
    boolean exist(String sessionId);

    /**
     * 创建新的会话映射对象
     *
     * @param sessionId 会话的唯一标识（非空）
     * @return true：创建成功（会话不存在）；false：创建失败（会话已存在）
     */
    boolean createSessionMap(String sessionId);

    /**
     * 启动会话垃圾回收机制
     * <p>
     * 定期检查并清理过期会话及会话内的过期属性，具体清理逻辑由实现类定义。
     * </p>
     *
     * @param checkTime          垃圾回收检查间隔时间（毫秒级）
     * @param maxNotUseTime      会话最大不活跃时长（毫秒级），超过此时长的会话将被清理
     * @param sessionNumThreshold 触发垃圾回收的会话数量阈值，低于该值时可能不执行清理
     */
    void sessionGC(long checkTime, long maxNotUseTime, long sessionNumThreshold);

    /**
     * 获取会话管理器的名称（用于标识和日志输出）
     *
     * @return 会话管理器名称
     */
    String sessionManagerName();

    /**
     * 为指定会话续期（更新最后使用时间）
     *
     * @param sessionId 会话的唯一标识（非空）
     */
    void expireAt(String sessionId);
}
