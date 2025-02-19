package top.heimi;

import org.turbo.core.config.ServerParamConfig;
import org.turbo.core.server.impl.DefaultTurboServer;

/**
 * TODO
 */
public class DemoTest {
    public static void main(String[] args) {
        DefaultTurboServer turboServer = new DefaultTurboServer(1);
        ServerParamConfig config = new ServerParamConfig();
        config.setShowRequestLog(true);
        turboServer.setConfig(config);
        turboServer.addMiddleware(new LoginMiddleware());
        turboServer.addExceptionHandler(GlobalExceptionHandler.class);
        turboServer.addController(TestClass.class);
        turboServer.start(8080);
    }
}
