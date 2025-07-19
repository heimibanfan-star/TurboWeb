package org.example.gateway;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class UserApplication {
    public static void main(String[] args) {
        // 配置网关
        Gateway gateway = new DefaultGateway();
        gateway.addServerNode("/order", "http://localhost:8081");

        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new UserController());
        BootStrapTurboWebServer.create()
                // 注册网关
                .protocol().gateway(gateway)
                .and()
                .http().routerManager(routerManager)
                .and().start();
    }
}
