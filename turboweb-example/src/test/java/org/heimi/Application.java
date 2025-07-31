package org.heimi;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

/**
 * TODO
 */
public class Application {

    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new HelloController());
        BootStrapTurboWebServer.create(1)
                .http().routerManager(routerManager)
                .and()
                .configServer(c -> {
                }).start();
    }
}
