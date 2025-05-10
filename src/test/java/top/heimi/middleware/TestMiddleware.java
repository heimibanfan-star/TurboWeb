package top.heimi.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.Middleware;

/**
 * TODO
 */
public class TestMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        System.out.println("Test 执行之前。。。");
        Object result = next(ctx);
        System.out.println("Test 执行之后。。。");
        return result;
    }
}
