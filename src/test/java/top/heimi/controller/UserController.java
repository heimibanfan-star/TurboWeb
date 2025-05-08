package top.heimi.controller;

import io.netty.handler.codec.http.*;
import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.sse.SseResponse;
import org.turbo.web.core.http.sse.SseSession;
import org.turbo.web.core.http.sse.SseResultObject;
import reactor.core.publisher.Mono;

/**
 * TODOd
 */
@RequestPath("/hello")
public class UserController {

    @Get
    public void hello(HttpContext ctx) throws InterruptedException {
        ctx.text("hello");
    }

    @Get("/limit")
    public void limit(HttpContext ctx) throws InterruptedException {
        ctx.text("hello world");
    }

    @Get("/err")
    public void err(HttpContext ctx) throws InterruptedException {
//        System.gc();
        int i = 1/0;
        ctx.text("hello world");
    }

    @Get("/reactive")
    public Mono<String> reactive(HttpContext ctx) {
        int i = 1/0;
        return Mono.just("hello world");
    }

    @Get("/sse")
    public HttpResponse sse(HttpContext ctx) throws InterruptedException {
        SseResponse sseResponse = ctx.newSseResponse();
        sseResponse.setSseCallback(sseSession -> {
            Thread.ofVirtual().start(() -> {
                for (int i = 0; i < 10; i++) {
                    sseSession.send("hello world" + i);
                    System.out.println("send");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        });
        return sseResponse;
    }

    @Get("/one")
    public void one(HttpContext ctx) throws InterruptedException {
        Thread.sleep(5000);
        ctx.text("one");
    }

    @Get("/two")
    public void two(HttpContext ctx) {
        ctx.text("two");
    }
}
