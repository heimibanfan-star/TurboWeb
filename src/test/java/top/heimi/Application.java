package top.heimi;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.middleware.CorsMiddleware;
import org.turbo.web.core.http.middleware.FreemarkerTemplateMiddleware;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.HelloController;
import top.heimi.controller.UserController;
import top.heimi.handler.GlobalExceptionHandler;
import top.heimi.handler.ReaGlobalExceptionHandler;

/**
 * TODO
 */
public class Application {

    private int num = 10;

    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(Application.class);
        server.addController(new HelloController(), new UserController());
        ServerParamConfig config = new ServerParamConfig();
        config.setShowRequestLog(false);
        server.addMiddleware(new CorsMiddleware(), new FreemarkerTemplateMiddleware());
        server.start();
    }
}