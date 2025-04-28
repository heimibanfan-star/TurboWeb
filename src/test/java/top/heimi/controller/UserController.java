package top.heimi.controller;

import io.netty.handler.codec.http.HttpResponse;
import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.sse.SSESession;
import org.turbo.web.core.http.sse.SseResultObject;
import reactor.core.publisher.Mono;

/**
 * TODOd
 */
@RequestPath("/hello")
public class UserController {

    @Get
    public void hello(HttpContext ctx) throws InterruptedException {
        Thread.sleep(50);
        ctx.text("hello");
    }

    @Get("/limit")
    public void limit(HttpContext ctx) throws InterruptedException {
        Thread.sleep(1000);
        ctx.text("hello world");
    }

    @Get("/reactive")
    public Mono<String> reactive(HttpContext ctx) {
        int i = 1/0;
        return Mono.just("hello world");
    }

    @Get("/sse")
    public Mono<HttpResponse> sse(HttpContext ctx) throws InterruptedException {
        SseResultObject sseResultObject = ctx.openSseSession();
        SSESession sseSession = sseResultObject.getSseSession();
        Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 10; i++) {
                sseSession.send("hello world");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
//        int i = 1/0;
        return Mono.just(sseResultObject.getHttpResponse());
    }
}
