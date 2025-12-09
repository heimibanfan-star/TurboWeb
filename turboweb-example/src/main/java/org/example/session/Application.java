package org.example.session;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;
import top.turboweb.http.session.BackHoleSessionManager;

public class Application {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new UserController());
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .replaceSessionManager(new MySessionManager())
                .and()
                .start(8080);
    }
}
