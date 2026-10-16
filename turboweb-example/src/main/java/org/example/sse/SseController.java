package org.example.sse;

import reactor.core.publisher.Flux;
import top.turboweb.rest.route.Get;
import top.turboweb.rest.route.Route;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;


@Route
public class SseController {

    @Get("/sse1")
    public SseResponse sse1(SseResponse sseResponse) {

        sseResponse.setSseCallback(session -> {
            Thread.ofVirtual().start(() -> {
                for (int i = 0; i < 10; i++) {
                    session.send("data:" + i + "\n\n");
                }
                session.close();
            });
        });
        return sseResponse;
    }

    @Get("/sse2")
    public SseResponse sse2(SseResponse sseResponse) {

        // 创建一个Flux流
        Flux<String> flux = Flux.just("hello1", "hello2", "hello3")
                .map(item -> "data:" + item + "\n\n");

        sseResponse.setSseCallback(flux);
        return sseResponse;
    }

    @Get("/sse3")
    public SseResponse sse3(SseResponse sseResponse) {
        // 创建一个Flux流
        Flux<String> flux = Flux.just(1, 2, 3)
                .doOnNext(item -> {
                    if (item == 2) throw new RuntimeException("测试异常");
                })
                .map(item -> "data:" + item + "\n\n");

        sseResponse.setSseCallback(flux, err -> "error:" + err.getMessage(), ConnectSession::close);
        return sseResponse;
    }

    @Get("/sse4")
    public SseResponse sse4(SseResponse sseResponse) {
        sseResponse.setFlux(Flux.just("Hello", "World"));
        return sseResponse;
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
