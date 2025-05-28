package top.turboweb.http.session;

import java.util.Map;

/**
 * session的容器代理
 */
public interface SessionManagerProxy {

    /**
     * 获取session
     *
     * @param sessionId sessionId
     * @return session
     */
    HttpSession getSession(String sessionId);

    /**
     * 添加session
     *
     * @param sessionId sessionId
     * @param httpSession   session
     */
    void addSession(String sessionId, HttpSession httpSession);

    /**
     * 获取所有session
     *
     * @return session
     */
    Map<String, HttpSession> getAllSession();
}
