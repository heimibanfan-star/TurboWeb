package org.example.websocket;


import top.turboweb.core.server.BootStrapTurboWebServer;

public class Application {
    public static void main(String[] args) {
        BootStrapTurboWebServer.create()
                .protocol()
                .websocket("/ws/(.*)", new MyWebSocketHandler(), 8)
                .and().start();
    }
}
