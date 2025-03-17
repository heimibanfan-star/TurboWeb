package org.turbo.web.core.http.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.router.dispatcher.HttpDispatcher;

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
