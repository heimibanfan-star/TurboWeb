package org.example.router;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.router.LambdaRouterGroup;

public class OrderController extends LambdaRouterGroup {

    @Override
    protected void registerRoute(RouterRegister register) {
        register.get(this::getOrder);
    }

    public String getOrder(HttpContext context) {
        return "Get Order";
    }
}
