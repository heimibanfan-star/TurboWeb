package top.turboweb.gateway.filter;

import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import top.turboweb.commons.exception.TurboGatewayException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步网关过滤器上下文。
 * <p>
 * 该类用于管理并执行一组基于 Reactor {@link Mono} 的异步过滤器，
 * 过滤器形成链式调用（Filter Chain），每个过滤器可决定是否继续后续执行。
 * <p>
 * 当所有过滤器均执行完毕或其中任意一个返回 false/抛出异常时，
 * 会通过 {@link ChannelPromise} 通知 Netty 处理结果（成功或失败）。
 */
public class AsyncGatewayFilterContext implements GatewayFilterContext<Mono<Boolean>> {

    private static final Logger log = LoggerFactory.getLogger(AsyncGatewayFilterContext.class);
    /**
     * 当前上下文中注册的过滤器列表。
     * 执行顺序与添加顺序一致。
     */
    private final List<GatewayFilter<Mono<Boolean>>> filters = new ArrayList<>();

    /**
     * 添加一个新的异步过滤器。
     *
     * @param filter 要添加的过滤器实例，不能为空。
     * @return 当前上下文（支持链式调用）
     * @throws IllegalArgumentException 如果过滤器已存在
     */
    @Override
    public GatewayFilterContext<Mono<Boolean>> addFilter(GatewayFilter<Mono<Boolean>> filter) {
        Objects.requireNonNull(filter, "filter can not be null");
        if (filters.contains(filter)) {
            throw new IllegalArgumentException("filter already exists");
        }
        filters.add(filter);
        return this;
    }

    /**
     * 启动过滤器链的执行。
     * <p>
     * 执行顺序为添加顺序，每个过滤器返回 {@code Mono<Boolean>}：
     * <ul>
     *     <li>返回 {@code true}：继续执行下一个过滤器。</li>
     *     <li>返回 {@code false}：终止链执行，触发 {@link TurboGatewayException}。</li>
     * </ul>
     * 执行结束后，通过 {@link ChannelPromise} 通知 Netty。
     *
     * @param request         当前 HTTP 请求
     * @param responseHelper  响应辅助工具
     * @param promise         Netty ChannelPromise，用于标识异步操作的完成状态
     */
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
     * 递归执行过滤器链。
     * <p>
     * 每个过滤器执行后返回 {@code Mono<Boolean>}：
     * <ul>
     *     <li>如果结果为 {@code true}，继续执行下一个过滤器。</li>
     *     <li>如果结果为 {@code false}，抛出 {@link TurboGatewayException}，中断整个链。</li>
     * </ul>
     *
     * @param request        当前 HTTP 请求
     * @param responseHelper 响应工具类
     * @param index          当前过滤器索引（通过 {@link AtomicInteger} 实现线程安全自增）
     * @return 表示整个链执行结果的 {@code Mono<Void>}
     */
    private Mono<Void> doFilter(FullHttpRequest request, ResponseHelper responseHelper, AtomicInteger index) {
        if (index.get() >= filters.size()) {
            return Mono.empty();
        }
        // 增加引用计数
        request.retain();
        return filters.get(index.getAndIncrement())
                .filter(request, responseHelper)
                // 释放引用
                .doFinally(signalType -> {
                    if (request.refCnt() <= 0) {
                        log.warn("Request already released before doFinally!");
                    }
                    request.release();
                })
                .flatMap(toNext -> {
                    // 如果返回false终止执行
                    if (!toNext) {
                        return Mono.error(new TurboGatewayException("filter return false"));
                    }
                    return doFilter(request, responseHelper, index);
                });
    }
}
