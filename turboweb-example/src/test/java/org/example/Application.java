package org.example;

import io.netty.buffer.ByteBuf;
import org.example.controller.HelloController;
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
        List<Object> list = new ArrayList<>(10000000);
        CountDownLatch latch = new CountDownLatch(1);
        int size = 1000000;
        for (int i = 0; i < size; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(100000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000);
            long start = System.nanoTime();
            Thread.ofVirtual().start(() -> {
                System.out.println("time" + (System.nanoTime() - start));
            });
        }
        latch.await();
    }

}

// 2221800ns 2761800ns 2180300ns
// 3055700ns 2920600ns 2742700ns
// 4219700ns 4477400ns 2295300ns
// 2430900ns 2997900ns 2708800ns
// 2488300ns 2330700ns 2567000ns

// time:10270500 time:104023000 time:360192900 time:13827012400