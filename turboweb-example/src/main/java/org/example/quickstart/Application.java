package org.example.quickstart;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class Application {
    public static void main(String[] args) {
        // 创建路由管理器
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new HelloController());
        // 配置并启动服务器
        BootStrapTurboWebServer.create(8)
                .http()
                .routerManager(routerManager)
                .and()
                .start("127.0.0.1", 8090);
    }
}
