package top.heimi;

import io.netty.handler.codec.http.multipart.FileUpload;
import org.turbo.anno.*;
import org.turbo.core.http.context.HttpContext;
import org.turbo.core.http.session.Session;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
    public void testPost(HttpContext ctx) throws IOException {
        List<FileUpload> files = ctx.getFileUploads("file");
        if (files != null) {
            FileUpload fileUpload = files.getFirst();
            // 获取字节数组
            byte[] bytes = fileUpload.get();
            System.out.println(bytes.length);
        }
        ctx.text("hello world");
    }
}
