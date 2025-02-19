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
        User user = ctx.loadJsonParamToBean(User.class);
        System.out.println(user);
        return "hello world";
    }
}
