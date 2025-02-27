package top.heimi.controller;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.session.Session;

import java.time.LocalDateTime;

@RequestPath("/user")
public class UserController {

    @Get
    public String index(HttpContext ctx) throws InterruptedException {
        return "index";
    }

    @Get("/test")
    public void test(HttpContext ctx) {
        Session session = ctx.getSession();
        ctx.json(session.getAttribute("test"));
    }
}
