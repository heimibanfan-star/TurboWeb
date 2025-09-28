package org.heimi;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;
import top.turboweb.http.middleware.router.LambdaRouterGroup;
import top.turboweb.http.middleware.router.LambdaRouterManager;
import top.turboweb.http.response.SseResponse;

/**
 * TODO
 */
public class OrderApplication {
    public static void main(String[] args) {
        LambdaRouterManager routerManager = new LambdaRouterManager();
        routerManager.addGroup(new LambdaRouterGroup() {
            @Override
            protected void registerRoute(RouterRegister register) {
                register.get("/order", (ctx) -> "order");
                register.get("/order/sse", ctx -> {
                    SseResponse sseResponse = ctx.createSseResponse();
                    sseResponse.setSseCallback(session -> {
                        for (int i = 0; i < 10; i++) {
                            session.send("hello world " + i);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        session.close();
                    });
                    return sseResponse;
                });
            }
        });

        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .start(8081);
    }
}
