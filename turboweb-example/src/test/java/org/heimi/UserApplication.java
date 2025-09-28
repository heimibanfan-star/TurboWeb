package org.heimi;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;
import top.turboweb.http.middleware.router.LambdaRouterGroup;
import top.turboweb.http.middleware.router.LambdaRouterManager;

/**
 * TODO
 */
public class UserApplication {
    public static void main(String[] args) {
        LambdaRouterManager routerManager = new LambdaRouterManager();
        routerManager.addGroup(new LambdaRouterGroup() {
            @Override
            protected void registerRoute(RouterRegister register) {
                register.get("/user", (ctx) -> "User");
            }
        });
        Gateway gateway = new DefaultGateway();
        gateway.addServerNode("/order", "http://127.0.0.1:8081");
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .protocol()
                .gateway(gateway)
                .and()
                .start(8080);
    }
}
