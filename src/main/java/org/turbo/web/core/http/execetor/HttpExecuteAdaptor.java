package org.turbo.web.core.http.execetor;

import io.netty.handler.codec.http.FullHttpRequest;
import org.turbo.web.core.http.response.HttpInfoResponse;

/**
 * http处理的适配器
 */
public interface HttpExecuteAdaptor {

    /**
     * http执行器的代理类
     *
     * @param request 完整的请求对象
     * @return org.turbo.core.http.response.HttpInfoResponse
     */
    HttpInfoResponse execute(FullHttpRequest request);

    /**
     * 是否显示请求日志
     *
     * @param showRequestLog true显示，false不显示
     */
    void setShowRequestLog(boolean showRequestLog);
}
