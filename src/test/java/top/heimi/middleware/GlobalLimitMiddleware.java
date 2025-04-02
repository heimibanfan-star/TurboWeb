package top.heimi.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.AbstractGlobalConcurrentLimitMiddleware;

/**
 * TODO
 */
public class GlobalLimitMiddleware extends AbstractGlobalConcurrentLimitMiddleware {
    public GlobalLimitMiddleware(int maxConcurrentLimit) {
        super(maxConcurrentLimit);
    }

    @Override
    public Object doAfterReject(HttpContext ctx) {
        return ctx.text("global reject");
    }
}
