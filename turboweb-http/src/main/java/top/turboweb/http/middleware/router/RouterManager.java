package top.turboweb.http.middleware.router;

import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.router.container.info.RouterDefinition;

/**
 * TurboWeb用于管理controller路由的抽象类
 */
public abstract class RouterManager extends Middleware {

    @Override
    public Object invoke(HttpContext ctx) {
        // 匹配路由的定义信息
        RouterDefinition routerDefinition = matchDefinition(ctx);
        // 如果没有路由定义信息，抛出异常
        if (routerDefinition == null) {
            throw new TurboRouterException(
                    String.format("router not found: %s %s", ctx.getRequest().getMethod(), ctx.getRequest().getUri()),
                    TurboRouterException.ROUTER_NOT_MATCH
            );
        }
        // 调用路由定义的
        try {
            return routerDefinition.invoke(ctx);
        } catch (Throwable e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            } else {
                throw new TurboRouterException(e, TurboRouterException.ROUTER_INVOKE_ERROR);
            }
        }
    }

    /**
     * 子类通过实现该方法根据自身策略匹配路由定义信息
     *
     * @param ctx 请求的上下文
     * @return 路由的定义信息
     */
    protected abstract RouterDefinition matchDefinition(HttpContext ctx);
}
