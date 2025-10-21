package top.turboweb.http.session;

/**
 * 会话管理器持有者接口，定义会话管理器的访问入口。
 * <p>
 * 该接口作为会话管理器的容器代理，隔离会话管理的具体实现与使用方，
 * 提供统一的会话管理器访问方式，便于后续切换不同的会话管理策略（如从内存存储切换到分布式存储）。
 * </p>
 * <p>
 * 实现类通常负责会话管理器的初始化、生命周期管理及垃圾回收机制的启动，
 * 使使用方无需关心底层实现细节，只需通过该接口获取{@link SessionManager}实例即可。
 * </p>
 */
public interface SessionManagerHolder {

    /**
     * 获取当前持有的会话管理器实例
     *
     * @return 会话管理器实例（非空）
     */
    SessionManager getSessionManager();
}
