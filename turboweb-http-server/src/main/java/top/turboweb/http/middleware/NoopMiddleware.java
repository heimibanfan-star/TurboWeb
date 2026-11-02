package top.turboweb.http.middleware;

import top.turboweb.http.context.HttpContext;

/**
 * 哑元中间件：链上的无逻辑占位节点，调用时直接透传至下一节点。
 * 用作中间件链的链头，业务中间件从第二环起挂载。
 */
public class NoopMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        return next(ctx);
    }
}
