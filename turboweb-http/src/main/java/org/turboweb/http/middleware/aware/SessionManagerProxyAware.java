package org.turboweb.http.middleware.aware;

import org.turboweb.http.session.SessionManagerProxy;

/**
 * 注入sessionManagerProxy的接口
 */
public interface SessionManagerProxyAware {

    /**
     * 设置sessionManagerProxy
     *
     * @param sessionManagerProxy sessionManagerProxy
     */
    void setSessionManagerProxy(SessionManagerProxy sessionManagerProxy);
}
