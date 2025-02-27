package org.turbo.web.core.http.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.config.ServerParamConfig;

import java.util.Map;

/**
 * 默认的session管理器代理实现
 */
public class DefaultSessionManagerProxy implements SessionManagerProxy {

    private static final Logger log = LoggerFactory.getLogger(DefaultSessionManagerProxy.class);
    /**
     * session管理器
     */
    private final SessionManager sessionManager;

    public DefaultSessionManagerProxy(SessionManager sessionManager, ServerParamConfig config) {
        this.sessionManager = sessionManager;
        // 启动session检测哨兵
        sessionManager.startSessionGC(
            config.getSessionCheckTime(),
            config.getSessionMaxNotUseTime(),
            config.getCheckForSessionNum()
        );
        log.info("session管理器初始化成功:{}", sessionManager.getSessionManagerName());
    }


    @Override
    public Session getSession(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    @Override
    public void addSession(String sessionId, Session session) {
        sessionManager.addSession(sessionId, session);
    }

    @Override
    public Map<String, Session> getAllSession() {
        return sessionManager.getAllSession();
    }
}
