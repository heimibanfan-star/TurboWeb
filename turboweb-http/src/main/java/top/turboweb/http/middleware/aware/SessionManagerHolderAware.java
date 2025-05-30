package top.turboweb.http.middleware.aware;

import top.turboweb.http.session.SessionManagerHolder;

/**
 * 注入sessionManagerProxy的接口
 */
public interface SessionManagerHolderAware {

    /**
     * 设置sessionManagerProxy
     *
     * @param sessionManagerHolder sessionManagerProxy
     */
    void setSessionManagerProxy(SessionManagerHolder sessionManagerHolder);
}
