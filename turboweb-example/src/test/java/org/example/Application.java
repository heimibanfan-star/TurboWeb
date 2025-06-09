package org.example;

import io.netty.buffer.ByteBuf;
import org.example.controller.HelloController;
import org.example.controller.UserController;
import top.turboweb.commons.senntinels.AutoDestructSentinel;
import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.websocket.AbstractWebSocketHandler;
import top.turboweb.websocket.WebSocketSession;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) throws InterruptedException {
        StandardTurboWebServer server = new StandardTurboWebServer(Application.class);
        server.controllers(new UserController());
        server.enableRequestSerial();
        server.config(config -> {
            config.setShowRequestLog(false);
        });
        server.start();
    }

}

// 2221800ns 2761800ns 2180300ns
// 3055700ns 2920600ns 2742700ns
// 4219700ns 4477400ns 2295300ns
// 2430900ns 2997900ns 2708800ns
// 2488300ns 2330700ns 2567000ns

// time:10270500 time:104023000 time:360192900 time:13827012400