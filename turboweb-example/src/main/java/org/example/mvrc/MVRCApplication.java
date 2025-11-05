package org.example.mvrc;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.router.AnnoRouterManager;
import top.turboweb.http.middleware.router.RouterManager;
import top.turboweb.http.middleware.router.VersionRouterManager;

import java.util.Random;

public class MVRCApplication {
    public static void main(String[] args) {

        // 创建两个路由管理器
        AnnoRouterManager v1Manager = new AnnoRouterManager(true);
        v1Manager.addController(new org.example.mvrc.v1.UserController());
        AnnoRouterManager v2Manager = new AnnoRouterManager(true);
        v2Manager.addController(new org.example.mvrc.v2.UserController());
        // 创建版本控制路由管理器
        VersionRouterManager routerManager = new VersionRouterManager() {
            @Override
            protected RouterManager getRouterManager(HttpContext context, Managers managers) {
                // 这里我们以随机的方式让新版本低频率的访问
                int num = new Random().nextInt();
                if (num % 3 == 0) {
                    return managers.getRouterManager("v2");
                }
                return managers.getRouterManager("v1");
            }
        };
        // 将路由管理器放入多版本控制路由管理器中
        routerManager.addRouterManager("v1", v1Manager);
        routerManager.addRouterManager("v2", v2Manager);

        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .start(8080);
    }
}
