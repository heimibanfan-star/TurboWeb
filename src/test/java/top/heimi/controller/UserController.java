package top.heimi.controller;

import io.netty.handler.codec.http.HttpResponse;
import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.response.ViewModel;
import org.turbo.web.core.http.session.Session;
import org.turbo.web.core.http.sse.SSESession;
import org.turbo.web.core.http.sse.SseResultObject;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RequestPath("/user")
public class UserController {

    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private List<SSESession> sessions = new ArrayList<>();


    @Get
    public void index(HttpContext ctx) {
        Map<String, String> map = new HashMap<>();
        map.put("name", "turbo");
        ctx.json(map);
    }

    @Get("/test")
    public Mono<String> test(HttpContext ctx) {
        return Mono.just("hello world");
    }

    @Get("/sse")
    public HttpResponse sse(HttpContext ctx) {
        SseResultObject sseResultObject = ctx.openSseSession();
        SSESession sseSession = sseResultObject.getSseSession();
        Thread.ofVirtual().start(() -> {
            while (true) {
                if (atomicInteger.get() > 20) {
                    sseSession.close();
                    break;
                }
                try {
                    Thread.sleep(1000);
                    sseSession.send("hello world" + atomicInteger.incrementAndGet());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        sessions.add(sseSession);
        sseSession.closeListener(() -> {
            sessions.remove(sseSession);
            System.out.println("连接断开");
            System.out.println(sessions.size());
        });
        return sseResultObject.getHttpResponse();
    }
}
