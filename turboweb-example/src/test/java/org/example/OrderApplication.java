package org.example;

import org.example.controller.OrderController;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;
import top.turboweb.http.middleware.router.AnnoRouterManager;

/**
 * TODO
 */
public class OrderApplication {
    public static void main(String[] args) {
        AnnoRouterManager manager = new AnnoRouterManager();
        manager.addController(new OrderController());
        Gateway gateway = new DefaultGateway();
        gateway.addServerNode("/user", "http://localhost:8080");
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(manager)
                .and()
                .protocol().gateway(gateway)
                .and()
                .start(8081);
    }
}
