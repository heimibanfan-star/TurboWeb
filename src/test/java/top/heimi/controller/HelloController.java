package top.heimi.controller;

import io.netty.handler.codec.http.HttpResponse;
import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.sse.SSESession;
import org.turbo.web.core.http.sse.SseResultObject;

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
           while (num < 20) {
               try {
                   Thread.sleep(1000);
                   session.send("hello world + " + num);
                   num++;
               } catch (InterruptedException e) {
                   throw new RuntimeException(e);
               }
           }
        });
        return sseResultObject.getHttpResponse();
    }
}
