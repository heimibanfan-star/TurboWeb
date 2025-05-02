package org.turbo.web.core.http.scheduler;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.concurrent.Promise;
import org.turbo.web.core.http.sse.SseSession;

/**
 * http处理的适配器
 */
public interface HttpScheduler {

    /**
     * http执行器的代理类
     *
     * @param request 完整的请求对象
     * @param promise 回调对象
     */
    void execute(FullHttpRequest request, Promise<HttpResponse> promise, SseSession session);

    /**
     * 是否显示请求日志
     *
     * @param showRequestLog true显示，false不显示
     */
    void setShowRequestLog(boolean showRequestLog);
}
