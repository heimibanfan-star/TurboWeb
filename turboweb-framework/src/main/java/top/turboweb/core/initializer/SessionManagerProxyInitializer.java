package top.turboweb.core.initializer;

import top.turboweb.core.config.ServerParamConfig;
import top.turboweb.http.session.SessionManager;
import top.turboweb.http.session.SessionManagerHolder;

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
    SessionManagerHolder init(ServerParamConfig config);
}
