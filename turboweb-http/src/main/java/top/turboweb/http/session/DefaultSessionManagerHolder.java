package top.turboweb.http.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 默认的session管理器代理实现
 */
public class DefaultSessionManagerHolder implements SessionManagerHolder {

    private static final Logger log = LoggerFactory.getLogger(DefaultSessionManagerHolder.class);
    /**
     * session管理器
     */
    private final SessionManager sessionManager;

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


    @Override
    public SessionManager getSessionManager() {
        return this.sessionManager;
    }
}
