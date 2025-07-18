package org.example.interceptor;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.interceptor.InterceptorManager;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class Application {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new UserController());
        InterceptorManager interceptorManager = new InterceptorManager();
        interceptorManager.addInterceptionHandler("/user/**", new OneInterceptor());
        interceptorManager.addInterceptionHandler("/user/**", new TwoInterceptor());
        BootStrapTurboWebServer.create()
                .http()
                .middleware(interceptorManager)
                .routerManager(routerManager)
                .and().start();
    }
}
