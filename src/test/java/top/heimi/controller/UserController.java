package top.heimi.controller;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.cookie.HttpCookie;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

    @Get
    public void index(HttpContext ctx) {
        HttpCookie httpCookie = ctx.getHttpCookie();
        httpCookie.setCookie("name", "turbo");
        httpCookie.setCookie("age", "18");
        ctx.json("hello world");
    }
}
