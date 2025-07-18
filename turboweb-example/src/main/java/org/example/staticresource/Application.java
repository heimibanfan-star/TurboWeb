package org.example.staticresource;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.view.StaticResourceMiddleware;

public class Application {

    public static void main(String[] args) {
        StaticResourceMiddleware staticResourceMiddleware = new StaticResourceMiddleware();
        staticResourceMiddleware.setCacheStaticResource(true);
        staticResourceMiddleware.setCacheFileSize(Integer.MAX_VALUE);
        staticResourceMiddleware.setStaticResourcePath("/static");
        staticResourceMiddleware.setStaticResourceUri("/static");
        BootStrapTurboWebServer.create()
                .http().middleware(staticResourceMiddleware)
                .and().start();
    }
}
