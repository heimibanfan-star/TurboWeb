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
    public Mono<String> index(HttpContext ctx) {
        return Mono.create(sink-> {
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                sink.success("hello world");
            });
        });
    }

    @Get("/test")
    public Mono<String> test(HttpContext ctx) {
        return Mono.just("hello world");
    }

}
