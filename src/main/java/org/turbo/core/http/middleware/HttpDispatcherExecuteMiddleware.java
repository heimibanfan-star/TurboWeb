package org.turbo.core.http.middleware;

import org.turbo.core.http.context.HttpContext;
import org.turbo.core.http.execetor.HttpDispatcher;

/**
 * 用于执行http请求分发器的middleware
 */
public class HttpDispatcherExecuteMiddleware extends Middleware {

    private final HttpDispatcher httpDispatcher;

    public HttpDispatcherExecuteMiddleware(HttpDispatcher httpDispatcher) {
        this.httpDispatcher = httpDispatcher;
    }

    @Override
    public Object invoke(HttpContext ctx) {
        return httpDispatcher.dispatch(ctx);
    }
}
