package top.turboweb.http.middleware;

import top.turboweb.http.base.context.HttpContext;

/**
 * 中间件接口
 */
public abstract class Middleware extends BaseMiddleware {

    /**
     * 中间件执行方法
     *
     * @param ctx 上下文
     * @return 执行结果
     */
    public abstract Object invoke(HttpContext ctx);

    /**
     * 执行下一个中间件
     *
     * @param ctx 上下文
     * @return 执行结果
     */
    protected Object next(HttpContext ctx) {
        if (getNext() == null) {
            return null;
        } else {
            return getNext().invoke(ctx);
        }
    }
}
