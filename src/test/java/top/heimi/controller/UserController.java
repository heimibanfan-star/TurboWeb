package top.heimi.controller;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;

/**
 * TODO
 */
@RequestPath("/user")
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
}
