package org.turboweb.core.http.scheduler;

import io.netty.handler.codec.http.FullHttpRequest;
import org.turboweb.core.http.connect.ConnectSession;

/**
 * http处理的适配器
 */
public interface HttpScheduler {

    /**
     * http执行器的代理类
     *
     * @param request 完整的请求对象
     */
    void execute(FullHttpRequest request, ConnectSession session);

    /**
     * 是否显示请求日志
     *
     * @param showRequestLog true显示，false不显示
     */
    void setShowRequestLog(boolean showRequestLog);
}
