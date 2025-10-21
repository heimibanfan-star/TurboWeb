package top.turboweb.gateway.filter;

import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import reactor.core.publisher.Mono;
import top.turboweb.commons.exception.TurboGatewayException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 阻塞式的过滤器上下文
 */
public class SyncGatewayFilterContext implements GatewayFilterContext<Boolean> {

    private final List<GatewayFilter<Boolean>> filters = new ArrayList<>();

    @Override
    public GatewayFilterContext<Boolean> addFilter(GatewayFilter<Boolean> filter) {
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
        // 增加引用
        request.retain();
        // 执行所有的过滤器
        Mono.<Boolean>create(sink -> {
                    Thread.ofVirtual().name("filter-execute-thread").start(() -> {
                        for (GatewayFilter<Boolean> filter : filters) {
                            Boolean toNext = filter.filter(request, responseHelper);
                            toNext = toNext != null && toNext;
                            if (!toNext) {
                                sink.error(new TurboGatewayException("filter return false, then cancel"));
                                break;
                            }
                        }
                        sink.success(true);
                    });
                })
                // 减少引用
                .doFinally(signalType -> request.release())
                .subscribe(
                        ok -> promise.setSuccess(),
                        promise::setFailure
                );
    }
}
