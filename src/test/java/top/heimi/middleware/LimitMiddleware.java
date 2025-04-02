package top.heimi.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.AbstractConcurrentLimitMiddleware;

/**
 * TODO
 */
public class LimitMiddleware extends AbstractConcurrentLimitMiddleware {

    @Override
    public Object doAfterReject(HttpContext ctx) {
        return ctx.text("reject");
    }
}
