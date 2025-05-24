package org.turboweb.http.middleware;

import org.turboweb.http.context.HttpContext;
import org.turboweb.http.router.dispatcher.HttpDispatcher;

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
