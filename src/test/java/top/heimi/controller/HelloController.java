package top.heimi.controller;

import io.netty.handler.codec.http.*;
import org.turbo.web.anno.*;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.sse.SseResponse;
import reactor.core.publisher.Mono;

/**
 * TODOd
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public void hello(HttpContext ctx) {
        ctx.text("hello world");
        int i = 1/0;
    }
}
