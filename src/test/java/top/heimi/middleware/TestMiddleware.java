package top.heimi.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.middleware.ReactiveMiddleware;
import reactor.core.publisher.Mono;

/**
 * TODO
 */
public class TestMiddleware extends ReactiveMiddleware {
    @Override
    public Mono<?> doSubscribe(HttpContext ctx) {
        return ctx.doNextMono();
    }
}
