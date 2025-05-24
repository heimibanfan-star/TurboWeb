package org.turboweb.core.http.middleware.aware;

import org.turboweb.core.http.session.SessionManagerProxy;

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
