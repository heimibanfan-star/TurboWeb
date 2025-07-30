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
                    c.setEnableHttpSchedulerLimit(true);
                    c.setHttpSchedulerLimitCount(1);
                    c.setHttpSchedulerLimitCacheThread(2);
                    c.setHttpSchedulerLimitTimeout(120000);
                    c.setMaxConnections(1);
                }).start();
    }
}
