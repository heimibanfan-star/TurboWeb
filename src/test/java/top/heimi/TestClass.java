package top.heimi;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.session.Session;

import java.io.IOException;

/**
 * TODO
 */
@RequestPath("/user/")
public class TestClass {

    @Get("/{name}/{age}/")
    public void test(HttpContext ctx) {
        String name = ctx.getPathVariable("name");
        String age = ctx.getPathVariable("age");
        System.out.println(age);
        System.out.println(name);
        ctx.json("hello world");
    }
}
