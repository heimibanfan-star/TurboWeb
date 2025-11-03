package org.example.gateway;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class OrderApplication {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager(true);
        routerManager.addController(new OrderController());

        BootStrapTurboWebServer.create()
                .http().routerManager(routerManager)
                .and()
                .start(8081);
    }
}
