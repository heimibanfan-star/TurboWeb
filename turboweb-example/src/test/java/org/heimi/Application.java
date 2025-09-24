package org.heimi;

import org.example.request.BindController;
import top.turboweb.commons.utils.thread.VirtualThreads;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;
import top.turboweb.http.middleware.view.StaticResourceMiddleware;

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
        StaticResourceMiddleware staticResourceMiddleware = new StaticResourceMiddleware();
        staticResourceMiddleware.setStaticResourcePath("E:/tmp");
        staticResourceMiddleware.setZeroCopy(true);

        BootStrapTurboWebServer.create(1)
                .http()
                .middleware(staticResourceMiddleware)
                .and()
                .start(8080);

    }
}
