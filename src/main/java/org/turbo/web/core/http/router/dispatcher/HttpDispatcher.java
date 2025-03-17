package org.turbo.web.core.http.router.dispatcher;

import org.turbo.web.core.http.context.HttpContext;

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
