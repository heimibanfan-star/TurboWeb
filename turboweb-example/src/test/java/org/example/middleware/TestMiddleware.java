package org.example.middleware;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;

/**
 * TODO
 */
public class TestMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        return next(ctx);
    }
}
