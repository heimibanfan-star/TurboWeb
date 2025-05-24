package org.turboweb.core.http.middleware;

import org.turboweb.core.http.context.HttpContext;
import org.turboweb.exception.TurboRequestRejectException;

/**
 * 哨兵节点的中间件
 */
public class SentinelMiddleware extends Middleware{
    @Override
    public Object invoke(HttpContext ctx) {
        String uri = ctx.getRequest().getUri();
        if (uri.contains("..")) {
            throw new TurboRequestRejectException("请求路径中存在非法字符");
        }
        return next(ctx);
    }
}
