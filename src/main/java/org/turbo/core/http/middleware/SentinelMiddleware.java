package org.turbo.core.http.middleware;

import org.turbo.core.http.context.HttpContext;

/**
 * 哨兵节点的中间件
 */
public class SentinelMiddleware extends Middleware{
    @Override
    public Object invoke(HttpContext ctx) {
        return null;
    }
}
