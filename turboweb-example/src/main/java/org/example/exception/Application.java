package org.example.exception;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class Application {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new ExcepController());
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .exceptionHandler(new GlobalExceptionHandler())
                .and()
                .start(8080);
    }
}
