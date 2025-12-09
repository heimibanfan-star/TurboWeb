package org.example.middleware;


import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;

public class AuthMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        // 获取请求对象
        FullHttpRequest request = ctx.getRequest();
        // 获取请求头
        String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (!"123456".equals(authorization)) {
            // 直接拒绝请求
            return "lose authorization";
        }
        // 调用后续的中间件
        return next(ctx);
    }
}
