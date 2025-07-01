package org.example;

import org.example.controller.UserController;
import org.example.middleware.TestMiddleware;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;
import top.turboweb.http.middleware.router.RouterManager;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new UserController());
        BootStrapTurboWebServer.create(Application.class)
                .http()
                .middleware(new TestMiddleware())
                .routerManager(routerManager)
                .and()
                .start(8080);
    }
}
