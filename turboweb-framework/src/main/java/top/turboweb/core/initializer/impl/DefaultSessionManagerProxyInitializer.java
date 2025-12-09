package top.turboweb.core.initializer.impl;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.http.base.session.MemorySessionManager;
import top.turboweb.http.base.session.SessionManager;
import top.turboweb.http.base.session.DefaultSessionManagerHolder;
import top.turboweb.http.base.session.SessionManagerHolder;
import top.turboweb.core.initializer.SessionManagerProxyInitializer;

/**
 * 默认的session管理器初始化器
 */
public class DefaultSessionManagerProxyInitializer implements SessionManagerProxyInitializer {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(DefaultSessionManagerProxyInitializer.class);
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
            config.getSessionCheckThreshold()
        );
        log.info("session管理器初始化完成");
        return proxy;
    }
}
