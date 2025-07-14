package org.example;

import org.example.controller.UserController;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;
import top.turboweb.http.middleware.router.AnnoRouterManager;
import top.turboweb.http.middleware.router.LambdaRouterGroup;
import top.turboweb.http.middleware.router.LambdaRouterManager;
import top.turboweb.http.session.BackHoleSessionManager;

import java.io.IOException;

/**
 * TODO
 */
public class UserApplication {
    public static void main(String[] args) {
        LambdaRouterManager manager = new LambdaRouterManager();
        manager.addGroup(new LambdaRouterGroup() {
            @Override
            public String requestPath() {
                return "/user";
            }

            @Override
            protected void registerRoute(RouterRegister register) {
                register.get("/", ctx -> "Hello World");
            }
        });
        Gateway gateway = new DefaultGateway();
        gateway.addServerNode("/order", "http://localhost:8081");
        BootStrapTurboWebServer.create()
                .http().routerManager(manager)
                .replaceSessionManager(new BackHoleSessionManager())
                .and()
                .protocol().gateway(gateway)
                .and()
                .start(8080);
    }
}
