package top.heimi.controller;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public void hello(HttpContext ctx) {
        ctx.json("hello world");
    }
}
