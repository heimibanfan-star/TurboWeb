package top.turboweb.http.middleware;

import io.netty.util.internal.TypeParameterMatcher;
import top.turboweb.http.context.HttpContext;


public abstract class CoreTypeMiddleware extends Middleware{
    /**
     * 前置钩子方法
     * 可在调用下一个中间件之前执行逻辑，例如权限检查、条件拦截等。
     *
     * @param ctx 当前请求上下文
     * @return true 继续执行下一个中间件，false 中断链条
     */
    protected boolean preNext(HttpContext ctx) {
        return true;
    }

    /**
     * 链条中断时的回调方法
     * 可在 preNext 返回 false 时执行自定义逻辑。
     *
     * @param ctx 当前请求上下文
     * @return 中断后的返回值，默认 null
     */
    protected Object onBreak(HttpContext ctx) {
        return null;
    }
}
