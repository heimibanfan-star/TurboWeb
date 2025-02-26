package top.heimi;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.UserController;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(8);
        ServerParamConfig config = new ServerParamConfig();
        config.setSessionCheckTime(10000);
        config.setCheckForSessionNum(1);
        server.addController(UserController.class);
        server.setConfig(config);
        server.start(8080);
    }
}
