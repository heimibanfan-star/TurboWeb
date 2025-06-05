package org.example;

import io.netty.buffer.ByteBuf;
import org.example.controller.HelloController;
import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.websocket.AbstractWebSocketHandler;
import top.turboweb.websocket.WebSocketSession;

import java.io.*;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) throws InterruptedException {
        new StandardTurboWebServer(Application.class)
                .controllers(new HelloController())
                .start(8080);
        Thread.ofVirtual().start(() -> {
           while (true) {
               try {
                   Thread.sleep(2000);
               } catch (InterruptedException e) {
                   throw new RuntimeException(e);
               }
               Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
               System.out.println("thread num:" + allStackTraces.size());
           }
        });
    }
}
