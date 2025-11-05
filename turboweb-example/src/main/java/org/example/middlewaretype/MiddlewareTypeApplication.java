package org.example.middlewaretype;

import org.reactivestreams.Publisher;
import reactor.core.CorePublisher;
import reactor.core.publisher.Mono;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.MixedMiddleware;
import top.turboweb.http.middleware.TypedMiddleware;
import top.turboweb.http.middleware.TypedSkipMiddleware;
import top.turboweb.http.middleware.router.LambdaRouterGroup;
import top.turboweb.http.middleware.router.LambdaRouterManager;


public class MiddlewareTypeApplication {
    public static void main(String[] args) {
        LambdaRouterManager routerManager = new LambdaRouterManager();
        routerManager.addGroup(new LambdaRouterGroup() {
            @Override
            protected void registerRoute(RouterRegister register) {
                register.get("/01", ctx -> "Hello World");
                register.get("/02", ctx -> 10L);
                register.get("/03", ctx -> Mono.just("hello world"));
            }
        });

        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                // 只关注于String类型
                .middleware(new MixedMiddleware() {
                    @Override
                    protected Object afterSyncNext(HttpContext ctx, Object result) {
                        System.out.println("同步的逻辑处理：" + result);
                        return result;
                    }

                    @Override
                    protected Publisher<?> afterAsyncNext(HttpContext ctx, Publisher<?> publisher) {
                        if (publisher instanceof Mono<?> mono) {
                            return mono.doOnNext(result -> System.out.println("异步的逻辑处理：" + result));
                        }
                        return publisher;
                    }
                })
                .and()
                .start(8080);
    }
}
