package top.heimi;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.middleware.FreemarkerTemplateMiddleware;
import org.turbo.web.core.http.middleware.StaticResourceMiddleware;
import org.turbo.web.core.http.middleware.TemplateMiddleware;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.UserController;
import top.heimi.handler.GlobalExceptionHandler;
import top.heimi.middleware.AuthMiddleware;
import top.heimi.middleware.ConfigMiddleware;
import top.heimi.middleware.TestMiddleware;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(Application.class, 8);
        server.addController(UserController.class);
        ServerParamConfig config = new ServerParamConfig();
        config.setShowRequestLog(false);
        server.setConfig(config);
        server.addExceptionHandler(GlobalExceptionHandler.class);
        server.addMiddleware(new ConfigMiddleware(), new AuthMiddleware());
        server.setIsReactiveServer(true);
//        server.setIsReactiveServer(true);
        server.start(8080);
    }
}
