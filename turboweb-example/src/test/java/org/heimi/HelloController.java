package org.heimi;
import top.turboweb.anno.Get;
import top.turboweb.anno.JsonModel;
import top.turboweb.anno.Post;
import top.turboweb.anno.RequestPath;


/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

    @Get
    public Result<User> hello() {
        User user = new User();
        user.setName("zhangsan");
        user.setAge(18);
        return new Result<>(user);
    }

    @Post
    public User user(@JsonModel User user) {
        return user;
    }

//    @Get
//    public HttpResponse test(HttpContext context) {
//        InternalConnectSession session = (InternalConnectSession) context.getConnectSession();
//        HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
//        response.setContent("hello world");
//        response.setContentType("text/plain");
//        session.getChannel().writeAndFlush(response);
//        return IgnoredHttpResponse.ignore();
//    }
}
