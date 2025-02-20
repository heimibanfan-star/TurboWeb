package top.heimi;

import org.turbo.anno.*;
import org.turbo.core.http.context.HttpContext;
import org.turbo.core.http.session.Session;

/**
 * TODO
 */
@RequestPath("/user")
public class TestClass {

    @Get("/set")
    public String test(HttpContext ctx) {
        Session session = ctx.getRequest().getSession();
        session.setAttribute("name", "zhangsan", 10000);
//        User user = ctx.loadJsonParamToBean(User.class);
//        System.out.println(user);
        return "successful";
    }

    @Get("/get")
    public void set(HttpContext ctx) {
        Session session = ctx.getRequest().getSession();
        String name = (String) session.getAttribute("name");
        ctx.json(name);
    }

    @Get("/remove")
    public void remove(HttpContext ctx) {
        Session session = ctx.getRequest().getSession();
        session.removeAttribute("name");
        ctx.json("successful");
    }

    @Post
    public void testPost(HttpContext ctx) {
        User user = ctx.loadValidJsonParamToBean(User.class);
        ctx.json(user);
    }
}
