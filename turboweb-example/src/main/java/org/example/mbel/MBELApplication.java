package org.example.mbel;

import io.netty.handler.codec.http.HttpHeaderNames;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.BranchMiddleware;
import top.turboweb.http.middleware.Middleware;

public class MBELApplication {
    public static void main(String[] args) {
        // 创建两组中间件，根据不用的Authorization中的数据执行不同的分支
        // 创建分支1的中间件
        Middleware m1forb1 = new Middleware() {
            @Override
            public Object invoke(HttpContext ctx) {
                System.out.println("branch1-middleware1");
                return next(ctx);
            }
        };
        Middleware m2forb1 = new Middleware() {
            @Override
            public Object invoke(HttpContext ctx) {
                System.out.println("branch1-middleware2");
                return next(ctx);
            }
        };
        // 创建分支2的中间件
        Middleware m1forb2 = new Middleware() {
            @Override
            public Object invoke(HttpContext ctx) {
                System.out.println("branch2-middleware1");
                return next(ctx);
            }
        };
        Middleware m2forb2 = new Middleware() {
            @Override
            public Object invoke(HttpContext ctx) {
                System.out.println("branch2-middleware2");
                return next(ctx);
            }
        };

        // 创建多分支执行管线
        BranchMiddleware branchMiddleware = new BranchMiddleware() {
            @Override
            protected String getBranchKey(HttpContext ctx) {
                // 获取请求头的数据
                return ctx.getRequest().headers().get(HttpHeaderNames.AUTHORIZATION);
            }
        };
        // 注册分支
        branchMiddleware
                .addMiddleware("admin", m1forb1)
                .addMiddleware("admin", m2forb1)
                .addMiddleware("user", m1forb2)
                .addMiddleware("user", m2forb2);
        // 添加主中间件
        Middleware m1 = new Middleware() {
            @Override
            public Object invoke(HttpContext ctx) {
                System.out.println("分支之前执行...");
                return next(ctx);
            }
        };
        Middleware m2 = new Middleware() {
            @Override
            public Object invoke(HttpContext ctx) {
                System.out.println("分支之后执行...");
                return "Hello World";
            }
        };
        // 启动服务器注册分支
        BootStrapTurboWebServer.create()
                .http()
                .middleware(m1)
                .middleware(branchMiddleware)
                .middleware(m2)
                .and()
                .start();
    }
}
