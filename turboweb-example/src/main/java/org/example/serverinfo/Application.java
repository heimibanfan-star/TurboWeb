package org.example.serverinfo;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.ServerInfoMiddleware;

public class Application {
    public static void main(String[] args) {
        ServerInfoMiddleware serverInfoMiddleware = new ServerInfoMiddleware();
        serverInfoMiddleware.setRequestPath("/infos");
        BootStrapTurboWebServer.create()
                .http().middleware(serverInfoMiddleware)
                .and().start();
    }
}
