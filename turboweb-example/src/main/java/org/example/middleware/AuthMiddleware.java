package org.example.middleware;


import io.netty.handler.codec.http.HttpHeaderNames;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.request.HttpInfoRequest;

public class AuthMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        // 获取请求对象
        HttpInfoRequest request = ctx.getRequest();
        // 获取请求头
        String authorization = request.getHeaders().get(HttpHeaderNames.AUTHORIZATION);
        if (!"123456".equals(authorization)) {
            // 直接拒绝请求
            return "lose authorization";
        }
        // 调用后续的中间件
        return next(ctx);
    }
}
