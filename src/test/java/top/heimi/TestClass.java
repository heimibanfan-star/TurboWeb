package top.heimi;

import org.turbo.anno.*;
import org.turbo.core.http.context.HttpContext;

/**
 * TODO
 */
@RequestPath("/user")
public class TestClass {

    @Get
    public String test(HttpContext ctx) {
        return "hello world";
    }

    @Post
    public void test2(HttpContext ctx) {
        ctx.text("hello world");
    }

    @Get("/user/{name}")
    public void test3(HttpContext ctx) throws InterruptedException {
        String name = ctx.getPathVariable("name");
        User user = new User(name, 18);
        Thread.sleep(50);
        ctx.json(user);
    }
}
