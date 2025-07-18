package org.example.interceptor;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.interceptor.InterceptorHandler;


public class TwoInterceptor implements InterceptorHandler {

    @Override
    public boolean preHandler(HttpContext ctx) {
        System.out.println("two preHandler");
        return false;
    }

    @Override
    public Object postHandler(HttpContext ctx, Object result) {
        System.out.println("two postHandler");
        return result;
    }

    @Override
    public void afterCompletion(Throwable exception) {
        System.out.println("two afterCompletion");
    }

    @Override
    public int order() {
        return 1;
    }
}
