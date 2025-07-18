package org.example.file;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class Application {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new FileController());
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .configServer(config -> {
                    config.setMaxContentLength(1024 * 1024 * 10);
                })
                .start(8080);
    }
}
