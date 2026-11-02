package top.turboweb.http.middleware;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.router.RouteHandlerManager;

import java.util.Objects;

/**
 * 路由调用中间件：装配期追加在中间件链的末端，把当前请求上下文交给
 * {@link RouteHandlerManager} 处理，其返回结果作为链结果交后续结果转化与写出。
 */
public class RouteInvokeMiddleware extends Middleware {

    private final RouteHandlerManager routeHandlerManager;

    public RouteInvokeMiddleware(RouteHandlerManager routeHandlerManager) {
        this.routeHandlerManager = Objects.requireNonNull(routeHandlerManager, "The route handler manager must not be null");
    }

    @Override
    public Object invoke(HttpContext ctx) {
        return routeHandlerManager.handle(ctx);
    }
}
