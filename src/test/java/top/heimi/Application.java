package top.heimi;

import io.netty.handler.codec.http.HttpMethod;
import org.turbo.web.core.http.middleware.CorsMiddleware;
import org.turbo.web.core.http.middleware.ServerInfoMiddleware;
import org.turbo.web.core.http.middleware.StaticResourceMiddleware;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.UserController;
import top.heimi.handler.GlobalExceptionHandler;
import top.heimi.middleware.GlobalLimitMiddleware;
import top.heimi.middleware.LimitMiddleware;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) throws InterruptedException {
        TurboServer server = new DefaultTurboServer(Application.class, 8);
        server.addController(new UserController());
        server.addMiddleware(
                new CorsMiddleware(),
                new ServerInfoMiddleware()
        );
        server.setIsReactiveServer(true);
        server.addExceptionHandler(new GlobalExceptionHandler());
        server.start();
    }
}
