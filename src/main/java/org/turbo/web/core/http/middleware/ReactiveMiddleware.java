package org.turbo.web.core.http.middleware;

import org.turbo.web.core.http.context.HttpContext;
import reactor.core.publisher.Mono;

/**
 * 反应式中间件
 */
public abstract class ReactiveMiddleware extends Middleware{
    @Override
    public Object invoke(HttpContext ctx) {
        return doSubscribe(ctx);
    }

    /**
     * 执行
     * @param ctx 上下文
     * @return mono对象
     */
    public abstract Mono<?> doSubscribe(HttpContext ctx);
}
