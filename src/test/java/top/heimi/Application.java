package top.heimi;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.middleware.FreemarkerTemplateMiddleware;
import org.turbo.web.core.http.middleware.StaticResourceMiddleware;
import org.turbo.web.core.http.middleware.TemplateMiddleware;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.UserController;
import top.heimi.middleware.ConfigMiddleware;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(Application.class, 8);
        ServerParamConfig config = new ServerParamConfig();
        config.setSessionCheckTime(10000);
        config.setSessionMaxNotUseTime(30000);
        config.setCheckForSessionNum(1);
        server.addController(UserController.class);
        server.setConfig(config);
        StaticResourceMiddleware staticResourceMiddleware = new StaticResourceMiddleware();
        TemplateMiddleware templateMiddleware = new FreemarkerTemplateMiddleware();
        server.addMiddleware(
            new ConfigMiddleware(),
            staticResourceMiddleware,
            templateMiddleware
        );
        server.start(8080);
    }
}
