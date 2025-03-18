package top.heimi.controller;

import io.netty.handler.codec.http.HttpResponse;
import org.turbo.web.anno.Get;
import org.turbo.web.anno.Post;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.response.ViewModel;
import org.turbo.web.core.http.sse.SSESession;
import org.turbo.web.core.http.sse.SseResultObject;
import top.heimi.pojo.User;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public void hello(HttpContext ctx) {
        ctx.json("hello world");
    }

    @Get("/sse")
    public HttpResponse sse(HttpContext ctx) {
        SseResultObject sseResultObject = ctx.openSseSession();
        SSESession session = sseResultObject.getSseSession();
        Thread.ofVirtual().start(() -> {
           int num = 0;
           while (num < 10) {
               try {
                   Thread.sleep(1000);
                   session.send("hello world + " + num);
                   num++;
               } catch (InterruptedException e) {
                   throw new RuntimeException(e);
               }
           }
           session.close();
        });
        return sseResultObject.getHttpResponse();
    }

    @Get("/t")
    public ViewModel viewModel(HttpContext ctx) {
        ViewModel viewModel = new ViewModel();
        viewModel.addAttribute("name", "张三");
        viewModel.setViewName("index");
        return viewModel;
    }

    @Post("/save")
    public void save(HttpContext ctx) {
        User user = ctx.loadJson(User.class);
        System.out.println(user);
        ctx.json("save success");
    }
}
