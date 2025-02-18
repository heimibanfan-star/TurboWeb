package org.turbo.core.http.execetor;

import org.turbo.core.http.context.HttpContext;
import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.core.http.response.HttpInfoResponse;

/**
 * http分发器
 */
public interface HttpDispatcher {

    /**
     * 执行http请求的分发操作
     *
     * @param ctx 请求上下文
     * @return 响应数据
     */
    Object dispatch(HttpContext ctx);
}
