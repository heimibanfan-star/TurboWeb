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
import top.heimi.pojos.Result;
import top.heimi.pojos.User;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RequestPath("/user")
public class UserController {

    private AtomicInteger atomicInteger = new AtomicInteger(0);
    private List<SSESession> sessions = new ArrayList<>();

//    @Get
//    public Mono<String> test(HttpContext ctx) {
//        return Mono.just("hello world");
//    }

    @Get
    public Mono<Result<User>> getUser(HttpContext ctx) {
        return Mono.deferContextual(context -> {
            // 获取上下文中的内容
            Object object = context.get("name");
            System.out.println(object);
            // 封装请求对象
            User user = ctx.loadValidQueryParamToBean(User.class);
            return Mono.just(new Result<>(200, user, "success"));
        });
    }

//    @Get
//    public Mono<String> index(HttpContext ctx) {
//        return Mono.just("hello world");
//    }
//
//    @Get("/test")
//    public Mono<String> test(HttpContext ctx) {
//        return Mono.just("hello world");
//    }
//
//    @Get("/sse")
//    public HttpResponse sse(HttpContext ctx) {
//        SseResultObject sseResultObject = ctx.openSseSession();
//        SSESession sseSession = sseResultObject.getSseSession();
//        Thread.ofVirtual().start(() -> {
//            while (true) {
//                if (atomicInteger.get() > 20) {
//                    sseSession.close();
//                    break;
//                }
//                try {
//                    Thread.sleep(1000);
//                    sseSession.send("hello world" + atomicInteger.incrementAndGet());
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        });
//        sessions.add(sseSession);
//        sseSession.closeListener(() -> {
//            sessions.remove(sseSession);
//            System.out.println("连接断开");
//            System.out.println(sessions.size());
//        });
//        return sseResultObject.getHttpResponse();
//    }
}
