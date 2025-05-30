package top.turboweb.http.session;

import java.util.Map;

/**
 * session的容器代理
 */
public interface SessionManagerHolder {

    /**
     * 获取session管理器
     * @return session管理器
     */
    SessionManager getSessionManager();
}
