package org.example.router;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;
import top.turboweb.http.middleware.router.LambdaRouterManager;

public class Application {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager("/api");
        routerManager.addController(new UserController());
        BootStrapTurboWebServer.create()
                .http().routerManager(routerManager)
                .and().start();
//        LambdaRouterManager routerManager = new LambdaRouterManager();
//        routerManager.addGroup(new OrderController());
//        BootStrapTurboWebServer.create()
//                .http().routerManager(routerManager)
//                .and().start();
    }
}
