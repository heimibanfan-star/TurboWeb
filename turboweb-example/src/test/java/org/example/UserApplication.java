package org.example;

import org.example.controller.UserController;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;
import top.turboweb.http.middleware.router.AnnoRouterManager;

import java.io.IOException;

/**
 * TODO
 */
public class UserApplication {
    public static void main(String[] args) {
        AnnoRouterManager manager = new AnnoRouterManager();
        manager.addController(new UserController());
        Gateway gateway = new DefaultGateway();
        gateway.addServerNode("/order", "http://localhost:8081");
        BootStrapTurboWebServer.create()
                .http().routerManager(manager)
                .and()
                .protocol().gateway(gateway)
                .and()
                .start(8080);
    }
}
