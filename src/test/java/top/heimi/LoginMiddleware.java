package top.heimi;

import org.turbo.core.http.context.HttpContext;
import org.turbo.core.http.middleware.Middleware;

/**
 * TODO
 */
public class LoginMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        return ctx.doNext();
    }
}
