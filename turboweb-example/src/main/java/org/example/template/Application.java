package org.example.template;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;
import top.turboweb.http.middleware.view.FreemarkerTemplateMiddleware;
import top.turboweb.http.middleware.view.TemplateMiddleware;

public class Application {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new UserController());
        // 创建模板中间件
        FreemarkerTemplateMiddleware templateMiddleware = new FreemarkerTemplateMiddleware();
        templateMiddleware.setTemplatePath("templates");
        templateMiddleware.setTemplateSuffix(".ftl");
        templateMiddleware.setOpenCache(true);
        BootStrapTurboWebServer.create()
                .http()
                .middleware(templateMiddleware)
                .routerManager(routerManager)
                .and().start();
    }
}
