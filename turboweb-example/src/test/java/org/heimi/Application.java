package org.heimi;

import top.turboweb.commons.utils.thread.VirtualThreads;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

import java.util.concurrent.TimeUnit;

/**
 * TODO
 */
public class Application {

    public static void main(String[] args) throws InterruptedException {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new HelloController());
        BootStrapTurboWebServer.create(1)
                .http().routerManager(routerManager)
                .and()
                .configServer(c -> {
                    c.setShowRequestLog(false);
                }).start(8081);
    }
}
