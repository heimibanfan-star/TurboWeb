package org.example.middleware;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;

public class OneMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        System.out.println("one pre");
        Object result = next(ctx);
        System.out.println("one after");
        return result;
    }
}
