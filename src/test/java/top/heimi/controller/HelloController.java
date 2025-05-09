package top.heimi.controller;

import org.turbo.web.anno.*;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.response.SseResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * TODOd
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public Mono<String> hello(HttpContext ctx) {
        return Mono.just("hello world");
    }

    @Get("/sse")
    public Mono<SseResponse> sse(HttpContext ctx) {
        SseResponse sseResponse = ctx.newSseResponse();
        Flux<Long> flux = Flux.interval(Duration.ofSeconds(1))
            .take(10);
        sseResponse.setSseCallback(flux);
        return Mono.just(sseResponse);
    }
}
