package top.turboweb.http.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 会话管理器持有者的默认实现，负责管理会话管理器的生命周期及访问入口。
 * <p>
 * 该类作为{@link SessionManager}的代理容器，在初始化时接收具体的会话管理器实例，并触发会话垃圾回收机制的启动。
 * 提供统一的会话管理器访问接口，隔离会话管理的具体实现与使用方，便于后续扩展和替换不同的会话管理策略。
 * </p>
 */
public class DefaultSessionManagerHolder implements SessionManagerHolder {

    private static final Logger log = LoggerFactory.getLogger(DefaultSessionManagerHolder.class);

    /**
     * 实际的会话管理器实例，由外部注入，负责处理具体的会话操作
     */
    private final SessionManager sessionManager;

    /**
     * 构造函数，初始化会话管理器并启动会话垃圾回收机制。
     * <p>
     * 初始化过程中会调用会话管理器的{@link SessionManager#sessionGC(long, long, long)}方法，
     * 根据传入的参数启动会话检测哨兵，定期清理过期会话，确保会话存储资源的合理利用。
     * 初始化完成后会记录日志，标识会话管理器已成功启动。
     * </p>
     *
     * @param sessionManager        具体的会话管理器实例，不能为空
     * @param checkTime             会话垃圾回收的检查间隔时间（单位：毫秒）
     * @param maxNotUseTime         会话的最大未使用时长（单位：毫秒），超过此时长的会话将被视为过期
     * @param checkForSessionNums   触发会话垃圾回收的会话数量阈值，当会话总数达到该值时才进行过期检查
     */
    public DefaultSessionManagerHolder(SessionManager sessionManager, long checkTime, long maxNotUseTime, long checkForSessionNums) {
        this.sessionManager = sessionManager;
        // 启动session检测哨兵
        sessionManager.sessionGC(
            checkTime,
            maxNotUseTime,
            checkForSessionNums
        );
        log.info("session管理器初始化成功:{}", sessionManager.sessionManagerName());
    }

    /**
     * 获取当前持有的会话管理器实例。
     * <p>
     * 提供统一的访问入口，使用方无需关心会话管理器的具体实现，只需通过该方法获取实例并调用相关接口即可。
     * </p>
     *
     * @return 当前持有的 {@link SessionManager}实例，非空
     */
    @Override
    public SessionManager getSessionManager() {
        return this.sessionManager;
    }
}
