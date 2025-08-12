package org.example.request;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class Application {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager(true);
        routerManager
//                .addController(new UserController())
                .addController(new BindController());
        BootStrapTurboWebServer.create()
                .http().routerManager(routerManager)
                .and().start();
    }
}
