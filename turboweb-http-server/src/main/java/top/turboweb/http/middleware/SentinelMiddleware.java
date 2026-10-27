package top.turboweb.http.middleware;

import top.turboweb.http.context.HttpContext;

/**
 * 哨兵节点的中间件
 */
public class SentinelMiddleware extends Middleware{
    @Override
    public Object invoke(HttpContext ctx) {
        return next(ctx);
    }
}
