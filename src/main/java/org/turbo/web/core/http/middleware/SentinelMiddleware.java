package org.turbo.web.core.http.middleware;

import org.turbo.web.core.http.context.HttpContext;

/**
 * 哨兵节点的中间件
 */
public class SentinelMiddleware extends Middleware{
    @Override
    public Object invoke(HttpContext ctx) {
        return ctx.doNext();
    }
}
