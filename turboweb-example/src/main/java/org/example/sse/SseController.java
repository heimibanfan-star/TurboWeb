package org.example.sse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.SseResponse;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RequestPath
public class SseController {

    @Get("/sse1")
    public SseResponse sse1(HttpContext context) {
        SseResponse sseResponse = context.createSseResponse();
        sseResponse.setSseCallback(session -> {
            Thread.ofVirtual().start(() -> {
                for (int i = 0; i < 10; i++) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    session.send("hello:" + i);
                }
                session.close();
            });
        });
        return sseResponse;
    }

    @Get("/sse2")
    public SseResponse sse2(HttpContext context) {
        SseResponse sseResponse = context.createSseResponse();
        Flux<String> flux = Flux.just("hello1", "hello2", "hello3").delayElements(Duration.ofSeconds(1));
        sseResponse.setSseCallback(flux);
        return sseResponse;
    }

    @Get("/sse3")
    public SseResponse sse3(HttpContext context) {
        SseResponse sseResponse = context.createSseResponse();
        // 创建一个Flux流，抛出异常
        Flux<Integer> flux = Flux.just(1, 2, 3)
                .delayElements(Duration.ofSeconds(1))
                .flatMap(i -> {
                    if (i == 3) {
                        return Mono.error(new RuntimeException("error"));
                    }
                    return Mono.just(i);
                });
        sseResponse.setSseCallback(flux, err -> "errMsg:" + err.getMessage(), ConnectSession::close);
        return sseResponse;
    }

    @Get("/sse4")
    public SseEmitter sse4(HttpContext context) {
        // sseEmitter可存储起来共享使用
        SseEmitter sseEmitter = context.createSseEmitter(32);
        // 发送数据
        Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                sseEmitter.send("hello:" + i);
            }
        });
        return sseEmitter;
    }

    @Get("/sse5")
    public SseEmitter sse5(HttpContext context) {
        SseEmitter sseEmitter = context.createSseEmitter();
        Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                sseEmitter.send("hello:" + i);
            }
            sseEmitter.close();
        });
        sseEmitter.onClose(emitter -> {
            System.out.println("close:" + emitter);
        });
        return sseEmitter;
    }

    @Get("/sse6")
    public SseEmitter sse6(HttpContext context) throws InterruptedException {
        TimeUnit.SECONDS.sleep(5);
        SseEmitter sseEmitter = context.createSseEmitter();
        return sseEmitter;
    }
}
