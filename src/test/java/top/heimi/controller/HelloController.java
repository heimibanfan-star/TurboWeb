package top.heimi.controller;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.cookie.HttpCookie;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.response.ViewModel;
import org.turbo.web.core.http.session.Session;
import org.turbo.web.core.http.sse.SSESession;
import org.turbo.web.core.http.sse.SseResultObject;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public Mono<HttpResponse> index(HttpContext ctx) {
        // 开启SSE会话
        SseResultObject sseResultObject = ctx.openSseSession();
        // 获取SSE会话
        SSESession session = sseResultObject.getSseSession();
        // 不断发送消息
        Thread thread = Thread.ofVirtual().start(() -> {
            while (true) {
                session.send("hello world");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    System.out.println("thread interrupt");
                    return;
                }
            }
        });
        // 监听session的销毁事件
        session.closeListener(() ->{
            thread.interrupt();
            System.out.println("session destroy");
        });
        // 通知浏览器，SSE会话已经建立
        return Mono.just(sseResultObject.getHttpResponse());
    }

    @Get("/test")
    public Mono<String> test(HttpContext ctx) {
        return Mono.just("hello world");
    }

}
