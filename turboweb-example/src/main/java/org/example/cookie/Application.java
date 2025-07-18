package org.example.cookie;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class Application {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new UserController());
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .configServer(config -> config.setShowRequestLog(false))
                .start(8080);
    }
}
