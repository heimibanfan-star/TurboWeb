package org.heimi;

import org.example.request.BindController;
import top.turboweb.commons.utils.thread.VirtualThreads;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * TODO
 */
public class Application {

    public static void main(String[] args) throws InterruptedException, NoSuchMethodException {
        AnnoRouterManager routerManager = new AnnoRouterManager(true);
        routerManager.addController(new HelloController());
        BootStrapTurboWebServer.create(1)
                .http().routerManager(routerManager)
                .and()
                .configServer(c -> {
                    c.setShowRequestLog(true);
                    c.setHttpSchedulerLimitCount(1);
                    c.setHttpSchedulerLimitTimeout(5);
                    c.setEnableHttpSchedulerLimit(true);
                }).start(8080);

    }
}
