package top.turboweb.gateway.filter;

import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import reactor.core.publisher.Mono;
import top.turboweb.commons.exception.TurboGatewayException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步的网关过滤器上下文
 */
public class AsyncGatewayFilterContext implements GatewayFilterContext<Mono<Boolean>> {

    private final List<GatewayFilter<Mono<Boolean>>> filters = new ArrayList<>();

    @Override
    public GatewayFilterContext<Mono<Boolean>> addFilter(GatewayFilter<Mono<Boolean>> filter) {
        Objects.requireNonNull(filter, "filter can not be null");
        if (filters.contains(filter)) {
            throw new IllegalArgumentException("filter already exists");
        }
        filters.add(filter);
        return this;
    }

    @Override
    public void startFilter(FullHttpRequest request, ResponseHelper responseHelper, ChannelPromise promise) {
        if (filters.isEmpty()) {
            promise.setSuccess();
            return;
        }
        final AtomicInteger index = new AtomicInteger(0);
        doFilter(request, responseHelper, index)
                .doOnSuccess(promise::setSuccess)
                .doOnError(promise::setFailure)
                .subscribe();
    }

    /**
     * 执行剩余的过滤器
     */
    private Mono<Void> doFilter(FullHttpRequest request, ResponseHelper responseHelper, AtomicInteger index) {
        if (index.get() >= filters.size()) {
            return Mono.empty();
        }
        return filters.get(index.getAndIncrement())
                .filter(request, responseHelper)
                .flatMap(toNext -> {
                    // 如果返回false终止执行
                    if (!toNext) {
                        return Mono.error(new TurboGatewayException("filter return false"));
                    }
                    return doFilter(request, responseHelper, index);
                });
    }
}
