package top.heimi.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.Middleware;

/**
 * TODO
 */
public class ConfigMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        return ctx.doNext();
    }

    @Override
    public void init(Middleware chain) {
        System.out.println("ConfigMiddleware init");
    }
}
