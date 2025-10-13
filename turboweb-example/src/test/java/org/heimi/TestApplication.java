package org.heimi;

import reactor.netty.http.HttpProtocol;
import reactor.netty.tcp.SslProvider;
import top.turboweb.client.DefaultTurboHttpClient;
import top.turboweb.client.TurboHttpClient;
import top.turboweb.client.engine.HttpClientEngine;
import top.turboweb.client.result.ClientResult;

import java.net.http.HttpClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;

/**
 * TODO
 */
public class TestApplication {
    public static void main(String[] args) throws InterruptedException {
        LongAdder longAdder = new LongAdder();
        int totalRequest = 10000;
        int clientNum = 100;
        int everyNum = totalRequest / clientNum;
        HttpClientEngine engine = new HttpClientEngine(config -> {
           config.ioThread(8);
           config.baseUrl("http://127.0.0.1:8081");
        });
        TurboHttpClient client = new DefaultTurboHttpClient(engine);
        CountDownLatch countDownLatch = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    for (int j = 0; j < 1000; j++) {
                        try {
                            client.request("/order");
                        } catch (Exception ignore) {

                        }
                    }
                } finally {
                    countDownLatch.countDown();
                }

            });
        }
        countDownLatch.await();
        CountDownLatch latch = new CountDownLatch(clientNum);
        long start = System.nanoTime();
        for (int i = 0; i < clientNum; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    for (int j = 0; j < everyNum; j++) {
                        try {
                            long reqStart = System.nanoTime();
                            ClientResult result = client.get("/order");
                            long reqEnd = System.nanoTime();
                            System.out.println((reqEnd - reqStart) / 1000 + " us");
                            int status = result.status();
                            if (status == 200) {
                                longAdder.increment();
                            }
                            result.release();
                        } catch (Exception ignore) {

                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        long end = System.nanoTime();
        System.out.println("QPS: " + longAdder.sum() / ((end - start) / 1000000000.0));
        System.out.println("Fail: " + (totalRequest - longAdder.sum()) / totalRequest);
        engine.close();
    }
}
