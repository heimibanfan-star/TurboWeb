package org.example.interceptors;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.interceptor.InterceptorHandler;

/**
 * TODO
 */
public class TwoInterceptor implements InterceptorHandler {

    @Override
    public boolean preHandler(HttpContext ctx) {
        System.out.println("two preHandler");
        return InterceptorHandler.super.preHandler(ctx);
    }

    @Override
    public Object postHandler(HttpContext ctx, Object result) {
        System.out.println("two postHandler");
        return InterceptorHandler.super.postHandler(ctx, result);
    }

    @Override
    public void afterCompletion(Throwable exception) {
        System.out.println("two afterCompletion");
        InterceptorHandler.super.afterCompletion(exception);
    }

    @Override
    public int order() {
        return 1;
    }
}
