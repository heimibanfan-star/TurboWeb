package top.turboweb.http.testkit.fixtures;

import top.turboweb.anno.Route;
import top.turboweb.anno.method.Get;
import top.turboweb.anno.method.Post;
import top.turboweb.http.context.HttpContext;

/**
 * 路由用例夹具：类级前缀为空的控制器。
 *
 * <p>显式写 {@code @Route("")}（而非完全不加类注解）是为了覆盖
 * {@code AnnoRouterManager.initRouterDefinition} 读取 {@code @Route} 的那条分支；
 * 它算出的 {@code prePath} 与"不加注解"同为 {@code ""}，因此对路径结果没有影响。
 *
 * <p>三个无注解方法 {@code zero/one/two} 不会被注册（初始化器先按"无注解则跳过"过滤），
 * 它们只作为 {@code MethodRouterDefinition} / {@code AutoBindRouterDefinition}
 * 直接构造用例的目标方法（这两个类的句柄解析要求目标类是 public 顶层类，
 * 所以它们不能放在测试类的嵌套类里）。
 */
@Route("")
public class PrefixProbeController {

    @Get("/ping")
    public String ping(HttpContext ctx) {
        return "pong";
    }

    @Post("/create")
    public String create(HttpContext ctx) {
        return "created";
    }

    public String zero() {
        return "zero";
    }

    public String one(String arg) {
        return arg == null ? "null-arg" : arg;
    }

    public String two(HttpContext ctx, String extra) {
        return "two";
    }
}
