package org.example.gateway;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class OrderApplication {
    public static void main(String[] args) {
        // 配置网关
        Gateway gateway = new DefaultGateway();
        gateway.addServerNode("/user", "http://localhost:8080");

        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new OrderController());

        BootStrapTurboWebServer.create()
                .protocol().gateway(gateway)
                .and()
                .http().routerManager(routerManager)
                .and().start(8081);
    }
}
