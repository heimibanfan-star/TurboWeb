package org.turbo.web.core.http.middleware.aware;

import org.turbo.web.core.http.session.SessionManagerProxy;

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
