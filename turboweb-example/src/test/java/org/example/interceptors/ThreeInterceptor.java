package org.example.interceptors;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.interceptor.InterceptorHandler;

/**
 * TODO
 */
public class ThreeInterceptor implements InterceptorHandler {

    @Override
    public boolean preHandler(HttpContext ctx) {
        System.out.println("three preHandler");
        int i = 1/0;
        return InterceptorHandler.super.preHandler(ctx);
    }

    @Override
    public Object postHandler(HttpContext ctx, Object result) {
        System.out.println("three postHandler");
        return InterceptorHandler.super.postHandler(ctx, result);
    }

    @Override
    public void afterCompletion(Throwable exception) {
        System.out.println("three afterCompletion");
        InterceptorHandler.super.afterCompletion(exception);
    }

    @Override
    public int order() {
        return 3;
    }
}
