package org.example.sse;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class Application {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new SseController());
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .start(8080);
    }
}
