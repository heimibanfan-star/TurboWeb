package org.heimi;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;
import top.turboweb.gateway.GatewayChannelHandler;
import top.turboweb.gateway.rule.ConfigRule;
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
        GatewayChannelHandler gatewayChannelHandler = new GatewayChannelHandler();
        gatewayChannelHandler.addService("orderService", "http://localhost:8081");
        ConfigRule rule = new ConfigRule();
        rule.addRule("/order/**", "orderService");
        gatewayChannelHandler.setRule(rule);
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .testGateway(gatewayChannelHandler)
                .start(8080);
    }
}
