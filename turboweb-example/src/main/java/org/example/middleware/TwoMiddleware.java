package org.example.middleware;


import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;

public class TwoMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        System.out.println("two");
        return "Two Middleware";
    }
}
