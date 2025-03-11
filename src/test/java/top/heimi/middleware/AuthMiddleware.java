package top.heimi.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.Middleware;

/**
 * TODO
 */
public class AuthMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        return ctx.json("end");
    }
}
