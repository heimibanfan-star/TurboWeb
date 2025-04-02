package top.heimi;

import io.netty.handler.codec.http.HttpMethod;
import org.turbo.web.core.http.middleware.CorsMiddleware;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.UserController;
import top.heimi.middleware.GlobalLimitMiddleware;
import top.heimi.middleware.LimitMiddleware;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) throws InterruptedException {
        TurboServer server = new DefaultTurboServer(Application.class);
        server.addController(new UserController());
        LimitMiddleware middleware = new LimitMiddleware();
        middleware.addStrategy(HttpMethod.GET, "/user/limit", 5);
        server.addMiddleware(new GlobalLimitMiddleware(10), middleware);
        server.addMiddleware(new CorsMiddleware());
        server.start();
    }
}
