package top.heimi;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.Middleware;

/**
 * TODO
 */
public class LoginMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        System.out.println("执行之前的逻辑...");
        Object object = ctx.doNext();
        System.out.println("执行之后的逻辑...");
        return object;
    }
}
