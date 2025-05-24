package org.turboweb.core.initializer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.config.ServerParamConfig;
import org.turboweb.core.http.session.DefaultSessionManagerProxy;
import org.turboweb.core.http.session.MemorySessionManager;
import org.turboweb.core.http.session.SessionManager;
import org.turboweb.core.http.session.SessionManagerProxy;
import org.turboweb.core.initializer.SessionManagerProxyInitializer;

/**
 * 默认的session管理器初始化器
 */
public class DefaultSessionManagerProxyInitializer implements SessionManagerProxyInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultSessionManagerProxyInitializer.class);
    private SessionManager sessionManager = new MemorySessionManager();

    @Override
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public SessionManagerProxy init(ServerParamConfig config) {
        SessionManagerProxy proxy =  new DefaultSessionManagerProxy(sessionManager, config);
        log.info("session管理器初始化完成");
        return proxy;
    }
}
