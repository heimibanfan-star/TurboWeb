package org.example.sse;

import reactor.core.publisher.Flux;
import top.turboweb.rest.annotation.router.Get;
import top.turboweb.rest.annotation.router.Route;
import top.turboweb.http.core.response.SseEmitter;


@Route
public class SseController {

    @Get("/sse1")
    public SseEmitter sse1(SseEmitter sseEmitter) {
        Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 10; i++) {
                sseEmitter.send("data:" + i + "\n\n");
            }
            sseEmitter.close();
        });
        return sseEmitter;
    }

    @Get("/sse2")
    public SseEmitter sse2(SseEmitter sseEmitter) {
        Flux.just("hello1", "hello2", "hello3")
                .subscribe(
                        sseEmitter::sendData,
                        err -> sseEmitter.send("error:" + err.getMessage()),
                        sseEmitter::close
                );
        return sseEmitter;
    }

    @Get("/sse3")
    public SseEmitter sse3(SseEmitter sseEmitter) {
        Flux.just(1, 2, 3)
                .doOnNext(item -> {
                    if (item == 2) throw new RuntimeException("测试异常");
                })
                .subscribe(
                        item -> sseEmitter.sendData(item + ""),
                        err -> sseEmitter.send("error:" + err.getMessage()),
                        sseEmitter::close
                );
        return sseEmitter;
    }

    @Get("/sse4")
    public SseEmitter sse4(SseEmitter sseEmitter) {
        Thread.ofVirtual().start(() -> {
            sseEmitter.sendId("1");
            sseEmitter.sendRetry(3000);
            sseEmitter.sendEvent("start");
            sseEmitter.sendData("Hello");
            sseEmitter.sendData("World");
            sseEmitter.close();
        });
        return sseEmitter;
    }

    @Get("/sse5")
    public SseEmitter sse5(SseEmitter sseEmitter) {
        // 创建线程发送信息
        Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 10; i++) {
                sseEmitter.sendData(i + "");
            }
            sseEmitter.close();
        });
        return sseEmitter;
    }

    @Get("/sse6")
    public SseEmitter sse6(SseEmitter sseEmitter) {
        Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 10; i++) {
                sseEmitter.sendData(i + "");
            }
            sseEmitter.close();
        });

        // 监听sse的关闭
        sseEmitter.onClose(emitter -> {
            System.out.println("SSE已关闭:" + emitter);
        });

        return sseEmitter;
    }

}
