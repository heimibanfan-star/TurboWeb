package org.example.response;

import io.netty.handler.codec.http.*;
import reactor.core.publisher.Flux;
import top.turboweb.anno.method.Get;
import top.turboweb.anno.RequestPath;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.http.response.HttpResult;
import top.turboweb.http.response.IgnoredHttpResponse;

@RequestPath
public class UserController {

    @Get("/user01")
    public String userO1(HttpContext context) {
        return "Hello World";
    }

    @Get("/user02")
    public User user02(HttpContext context) {
        User user = new User();
        user.setName("张三");
        user.setAge(18);
        return user;
    }

    @Get("/user03")
    public HttpResult<User> user03(HttpContext context) {
        User user = new User();
        user.setName("张三");
        user.setAge(18);
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set("name", "TurboWeb");
        return HttpResult.create(200, headers, user);
    }

    @Get("/resp")
    public HttpResponse resp(HttpContext context) {
        HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
        response.setContent("hello world");
        response.setContentType("text/plain");
        return response;
    }

    @Get("/stream")
    public Flux<String> stream(HttpContext context) {
        return Flux.just("你好", "世界");
    }

    @Get("/ignore")
    public HttpResponse ignore(HttpContext context) {
        // 创建响应对象
        HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
        response.setContent("hello world");
        response.setContentType("text/plain");
        // 获取连接会话
        InternalConnectSession session = (InternalConnectSession) context.getConnectSession();
        // 发送响应
        session.getChannel().writeAndFlush(response);
        return IgnoredHttpResponse.ignore();
    }
}
