package top.turboweb.http.middleware;

import top.turboweb.http.context.HttpContext;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局并发控制器
 */
public abstract class AbstractGlobalConcurrentLimitMiddleware extends Middleware{

    private final int maxConcurrentLimit;
    private final AtomicInteger count = new AtomicInteger(0);

    public AbstractGlobalConcurrentLimitMiddleware(int maxConcurrentLimit) {
        this.maxConcurrentLimit = maxConcurrentLimit;
    }

    @Override
    public Object invoke(HttpContext ctx) {
        boolean flag = couldEnter();
        if (!flag) {
            return doAfterReject(ctx);
        }
        // 调用后续中间件
        try {
            return next(ctx);
        } finally {
            leave();
        }
    }

    /**
     * 请求进入
     * @return 是否允许进入
     */
    private boolean couldEnter() {
        if (count.incrementAndGet() > maxConcurrentLimit) {
            count.decrementAndGet();
            return false;
        }
        return true;
    }

    /**
     * 请求离开
     */
    private void leave() {
        count.decrementAndGet();
    }

    /**
     * 拒绝请求执行的回调
     * @param ctx 上下文
     * @return 返回值
     */
    public abstract Object doAfterReject(HttpContext ctx);
}
