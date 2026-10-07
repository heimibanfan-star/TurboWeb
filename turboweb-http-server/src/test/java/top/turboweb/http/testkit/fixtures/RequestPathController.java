package top.turboweb.http.testkit.fixtures;

import top.turboweb.anno.RequestPath;
import top.turboweb.anno.method.Get;
import top.turboweb.anno.method.Post;
import top.turboweb.http.context.HttpContext;

/**
 * 路由用例夹具：类级 {@code @RequestPath} 前缀 + 一个路径参数路由。
 *
 * <p>放在 {@code testkit/fixtures} 子包：本类不被任何扫描器拾取
 * （{@code AnnoRouterManager} 无类路径扫描），集中放置以免将来引入扫描时
 * 污染"注册了几个路由"的计数断言。
 */
@RequestPath("/fixture")
public class RequestPathController {

    @Get("/ping")
    public String ping(HttpContext ctx) {
        return "pong";
    }

    @Post("/echo")
    public String echo(HttpContext ctx) {
        return "echoed";
    }

    @Get("/user/{id}")
    public String user(HttpContext ctx) {
        return "user:" + ctx.param("id");
    }
}
