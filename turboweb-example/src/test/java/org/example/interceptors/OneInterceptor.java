package org.example.interceptors;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.interceptor.InterceptorHandler;

/**
 * TODO
 */
public class OneInterceptor implements InterceptorHandler {

    @Override
    public boolean preHandler(HttpContext ctx) {
        System.out.println("one preHandler");
        return true;
    }

    @Override
    public Object postHandler(HttpContext ctx, Object result) {
        System.out.println("one postHandler");
        return InterceptorHandler.super.postHandler(ctx, result);
    }

    @Override
    public void afterCompletion(Throwable exception) {
        System.out.println("one afterCompletion");
        InterceptorHandler.super.afterCompletion(exception);
    }

    @Override
    public int order() {
        return 0;
    }
}
