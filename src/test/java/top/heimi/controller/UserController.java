package top.heimi.controller;

import io.netty.handler.codec.http.HttpResponse;
import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.sse.SseResultObject;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

    @Get("/sse")
    public HttpResponse index(HttpContext ctx) {
        SseResultObject sseResultObject = ctx.openSseSession();
        Thread.ofVirtual().start(() ->{
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                sseResultObject.getSseSession().send("hello world");
            }
        });
        return sseResultObject.getHttpResponse();
    }
}
