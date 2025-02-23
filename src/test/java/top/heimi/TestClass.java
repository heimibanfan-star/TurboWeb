package top.heimi;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.session.Session;

import java.io.IOException;

/**
 * TODO
 */
@RequestPath("/user")
public class TestClass {

    @Get("/set")
    public String test(HttpContext ctx) throws InterruptedException {
        Session session = ctx.getRequest().getSession();
        session.setAttribute("name", "zhangsan", 10000);
//        User user = ctx.loadJsonParamToBean(User.class);
//        System.out.println(user);
        Thread.sleep(50);
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

    @Get
    public void test01(HttpContext ctx) throws IOException {
        ctx.text("hello world");
    }
}
