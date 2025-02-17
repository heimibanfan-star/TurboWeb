package top.heimi;

import org.turbo.core.server.impl.DefaultTurboServer;

/**
 * TODO
 */
public class DemoTest {
    public static void main(String[] args) {
        DefaultTurboServer turboServer = new DefaultTurboServer(1);
        turboServer.addController(TestClass.class);
        turboServer.start(8080);
    }
}
