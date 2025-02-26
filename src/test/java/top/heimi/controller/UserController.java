package top.heimi.controller;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;

import java.time.LocalDateTime;

@RequestPath("/user")
public class UserController {

    @Get
    public void index(HttpContext ctx) throws InterruptedException {
        Thread.sleep(300);
        ctx.json(LocalDateTime.now());
    }
}
