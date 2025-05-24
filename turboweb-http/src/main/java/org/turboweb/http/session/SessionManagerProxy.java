package org.turboweb.http.session;

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
    Session getSession(String sessionId);

    /**
     * 添加session
     *
     * @param sessionId sessionId
     * @param session   session
     */
    void addSession(String sessionId, Session session);

    /**
     * 获取所有session
     *
     * @return session
     */
    Map<String, Session> getAllSession();
}
