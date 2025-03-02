package top.heimi.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.ReactiveMiddleware;
import reactor.core.publisher.Mono;
import top.heimi.pojos.Result;

/**
 * TODO
 */
public class AuthMiddleware extends ReactiveMiddleware {
    @Override
    public Mono<?> doSubscribe(HttpContext ctx) {
        return ctx.doNextMono()
            .map(r ->{
                System.out.println("执行之后的逻辑...");
                return r;
            })
            .contextWrite(context -> context.put("name", "zhangsan"));
    }
}
