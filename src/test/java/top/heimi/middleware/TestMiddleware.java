package top.heimi.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.Middleware;
import reactor.core.CorePublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * TODO
 */
public class TestMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        return Mono.just("111111")
            .flatMapMany(s -> ctx.doSubscribe());
    }
}
