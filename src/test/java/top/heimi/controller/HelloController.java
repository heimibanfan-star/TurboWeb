package top.heimi.controller;

import org.turbo.web.anno.*;
import org.turbo.web.core.http.context.HttpContext;
import reactor.core.publisher.Mono;

/**
 * TODOd
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public Mono<String> hello(HttpContext ctx) {
        return Mono.just("hello world");
    }
}
