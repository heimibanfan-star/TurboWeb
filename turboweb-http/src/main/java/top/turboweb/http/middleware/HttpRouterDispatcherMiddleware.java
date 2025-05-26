package top.turboweb.http.middleware;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.router.dispatcher.HttpDispatcher;

/**
 * 用于执行http请求分发器的middleware
 */
public class HttpRouterDispatcherMiddleware extends Middleware {

    private final HttpDispatcher httpDispatcher;

    public HttpRouterDispatcherMiddleware(HttpDispatcher httpDispatcher) {
        this.httpDispatcher = httpDispatcher;
    }

    @Override
    public Object invoke(HttpContext ctx) {
        return httpDispatcher.dispatch(ctx);
    }
}
