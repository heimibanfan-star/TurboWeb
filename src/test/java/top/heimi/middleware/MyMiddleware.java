package top.heimi.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.middleware.ReactiveMiddleware;
import org.turbo.web.core.http.middleware.aware.CharsetAware;
import org.turbo.web.core.http.middleware.aware.ExceptionHandlerMatcherAware;
import org.turbo.web.core.http.middleware.aware.MainClassAware;
import org.turbo.web.core.http.middleware.aware.SessionManagerProxyAware;
import org.turbo.web.core.http.session.SessionManagerProxy;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;

/**
 * TODO
 */
public class MyMiddleware extends ReactiveMiddleware {


    @Override
    public Mono<?> doSubscribe(HttpContext ctx) {
        return ctx.doNextMono()
            .map(r -> {
                System.out.println(Thread.currentThread().getName());
                return r;
            });
    }
}
