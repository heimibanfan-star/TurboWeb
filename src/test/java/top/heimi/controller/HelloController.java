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
    public void helloGet(HttpContext ctx) {
        ctx.text("Get");
    }

    @Get("/{id}")
    public void helloGet1(HttpContext ctx) {
        String id = ctx.param("id");
        ctx.text("Get " + id);
    }

    @Post
    public void helloPost(HttpContext ctx) {
        ctx.text("Post");
    }

    @Patch
    public void helloPatch(HttpContext ctx) {
        ctx.text("Patch");
    }

    @Patch("/{id}")
    public void helloPatch1(HttpContext ctx) {
        String id = ctx.param("id");
        ctx.text("Patch " + id);
    }

    @Put
    public void helloPut(HttpContext ctx) {
        ctx.text("Put");
    }

    @Delete
    public void helloDelete(HttpContext ctx) {
        ctx.text("Delete");
    }
}
