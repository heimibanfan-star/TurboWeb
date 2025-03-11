package top.heimi;

import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.HelloController;
import top.heimi.middleware.AuthMiddleware;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(Application.class, 8);
        server.addController(new HelloController());
        server.addMiddleware(new AuthMiddleware());
        server.start();
    }
}
