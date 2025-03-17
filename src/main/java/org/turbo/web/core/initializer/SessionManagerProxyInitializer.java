package org.turbo.web.core.initializer;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.session.SessionManager;
import org.turbo.web.core.http.session.SessionManagerProxy;

/**
 * session管理器初始化器
 */
public interface SessionManagerProxyInitializer {

    /**
     * 设置session管理器
     *
     * @param sessionManager session管理器
     */
    void setSessionManager(SessionManager sessionManager);

    /**
     * 初始化session管理器
     *
     * @return session管理器代理对象
     */
    SessionManagerProxy init(ServerParamConfig config);
}
