package org.example.response;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.http.response.HttpResult;

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
}
