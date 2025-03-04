package top.heimi;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.middleware.AbstractStaticResourceMiddleware;
import org.turbo.web.core.http.middleware.FreemarkerTemplateMiddleware;
import org.turbo.web.core.http.middleware.ReactiveStaticResourceMiddleware;
import org.turbo.web.core.http.middleware.StaticResourceMiddleware;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.HelloController;
import top.heimi.controller.UserController;
import top.heimi.init.ServerInitConfig;
import top.heimi.middleware.MyMiddleware;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(Application.class, 8);
        server.addController(HelloController.class);
        // 切换为反应式编程
        server.setIsReactiveServer(true);
        AbstractStaticResourceMiddleware staticResourceMiddleware = new ReactiveStaticResourceMiddleware();
//        staticResourceMiddleware.setCacheStaticResource(false);
        ServerParamConfig config = new ServerParamConfig();
        config.setShowRequestLog(false);
        server.setConfig(config);
        server.addMiddleware(staticResourceMiddleware);
        server.start(8080);
    }
}
