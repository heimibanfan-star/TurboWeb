package top.turboweb.http.middleware;

import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.http.context.HttpContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 抽象的并发控制中间件
 */
public abstract class AbstractConcurrentLimitMiddleware extends Middleware{

    private static final Logger log = LoggerFactory.getLogger(AbstractConcurrentLimitMiddleware.class);
    protected final Map<String, Map<String, ConcurrentLimit>> concurrentLimitMap;

    {
        concurrentLimitMap = new HashMap<>();
    }

    @Override
    public Object invoke(HttpContext ctx) {
        // 判断是否可以放行
        LimitMatchResult limitMatchResult = couldEnter(ctx.getRequest().method().name(), ctx.getRequest().uri());
        if (limitMatchResult.status == LimitMatchStatus.REJECT) {
            return doAfterReject(ctx);
        }
        // 进入后续处理器
        try {
            return next(ctx);
        } finally {
            leave(ctx.getRequest().method().name(), limitMatchResult);
        }
    }

    /**
     * 添加并发控制的策略
     *
     * @param method 请求方式
     * @param prefix 请求前缀
     * @param limit 限制
     */
    public void addStrategy(HttpMethod method, String prefix, int limit) {
        String httpMethodName = method.name();
        if (!concurrentLimitMap.containsKey(httpMethodName)) {
            concurrentLimitMap.put(httpMethodName, new HashMap<>());
        }
        Map<String, ConcurrentLimit> atomicIntegerMap = concurrentLimitMap.get(httpMethodName);
        if (atomicIntegerMap.containsKey(prefix)) {
            log.warn("并发控制策略已存在,方法:{},前缀:{}", httpMethodName, prefix);
        }
        ConcurrentLimit concurrentLimit = new ConcurrentLimit(limit);
        atomicIntegerMap.put(prefix, concurrentLimit);
    }

    /**
     * 请求被拒绝时执行的回调
     * @param ctx 上下文
     * @return 返回值
     */
    public abstract Object doAfterReject(HttpContext ctx);

    /**
     * 判断是否可以进入后续中间件
     * @param httpMethod 请求方法
     * @param url 请求路径
     * @return 计数器的前缀，null不参与控制
     */
    protected LimitMatchResult couldEnter(String httpMethod, String url) {
        if (!concurrentLimitMap.containsKey(httpMethod)) {
            return new LimitMatchResult(LimitMatchStatus.IGNORE, null);
        }
        Map<String, ConcurrentLimit> atomicIntegerMap = concurrentLimitMap.get(httpMethod);
        for (Map.Entry<String, ConcurrentLimit> entry : atomicIntegerMap.entrySet()) {
            String prefix = entry.getKey();
            if (!url.startsWith(prefix)) {
                continue;
            }
            // 增长引用计数
            if (entry.getValue().count.incrementAndGet() > entry.getValue().limit) {
                entry.getValue().count.decrementAndGet();
                return new LimitMatchResult(LimitMatchStatus.REJECT, prefix);
            } else {
                return new LimitMatchResult(LimitMatchStatus.ALLOW, prefix);
            }
        }
        return new LimitMatchResult(LimitMatchStatus.IGNORE, null);
    }

    /**
     * 请求离开计数器
     * @param httpMethod 请求方法
     * @param limitMatchResult 匹配结果
     */
    protected void leave(String httpMethod, LimitMatchResult limitMatchResult) {
        if (limitMatchResult.status != LimitMatchStatus.ALLOW) {
            return;
        }
        Map<String, ConcurrentLimit> atomicIntegerMap = concurrentLimitMap.get(httpMethod);
        ConcurrentLimit concurrentLimit = atomicIntegerMap.get(limitMatchResult.prefix);
        if (concurrentLimit == null) {
            return;
        }
        concurrentLimit.count.decrementAndGet();
    }

    /**
     * 并发控制策略
     */
    protected static class ConcurrentLimit {
        private final AtomicInteger count;
        private final int limit;

        public ConcurrentLimit(int limit) {
            this.limit = limit;
            this.count = new AtomicInteger(0);
        }
    }

    protected record LimitMatchResult(LimitMatchStatus status, String prefix) {
    }

    protected enum LimitMatchStatus {
        REJECT(),
        IGNORE(),
        ALLOW();
    }
}
