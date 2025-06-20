package top.turboweb.core.initializer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.http.session.*;
import top.turboweb.http.session.DefaultSessionManagerHolder;
import top.turboweb.http.session.SessionManagerHolder;
import top.turboweb.core.initializer.SessionManagerProxyInitializer;

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
    public SessionManagerHolder init(HttpServerConfig config) {
        SessionManagerHolder proxy =  new DefaultSessionManagerHolder(
            sessionManager,
            config.getSessionCheckTime(),
            config.getSessionMaxNotUseTime(),
            config.getCheckForSessionNum()
        );
        log.info("session管理器初始化完成");
        return proxy;
    }
}
