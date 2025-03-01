package top.heimi.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.Middleware;

/**
 * TODO
 */
public class TestMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        System.out.println("22222 start");
        Object object = ctx.doNext();
        System.out.println("22222 end");
        return object;
    }
}
