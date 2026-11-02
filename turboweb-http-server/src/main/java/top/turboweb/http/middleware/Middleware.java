package top.turboweb.http.middleware;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.http.context.HttpContext;

/**
 * 中间件抽象基类：链式结构（next 指针与锁定）、生命周期（init 与 destroy）与调用语义（invoke）在此统一定义。
 */
public abstract class Middleware {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(Middleware.class);
    private Middleware next;
    private boolean isLock = false;

    public Middleware getNext() {
        return next;
    }

    public final void setNext(Middleware next) {
        if (isLock) {
            log.warn("Middleware is locked, can not set next middleware");
        } else {
            this.next = next;
        }
    }

    /**
     * 锁定中间件，不允许设置下一个中间件
     */
    public final void lockMiddleware() {
        isLock = true;
    }

    /**
     * 初始化
     *
     * @param chain 中间件链
     */
    public void init(Middleware chain) {
    }

    /**
     * 销毁中间件，释放 {@link #init(Middleware)} 阶段创建的资源（线程池、定时任务、缓存等）。
     * <p>
     * 默认空实现，只有持有生命周期资源的子类才需要覆写。调用由应用发起：
     * 应用关闭时沿中间件链按与装配期 {@code init} 相同的顺序（链头到链尾）
     * 依次调用各中间件的本方法，因此每个实例在一次应用生命周期内最多被调用一次。
     * <p>
     * 覆写时须保证幂等，并自行捕获记录内部失败，不要让异常中断同链其他中间件的销毁。
     */
    public void destroy() {
    }

    /**
     * 中间件执行方法
     *
     * @param ctx 上下文
     * @return 执行结果
     */
    public abstract Object invoke(HttpContext ctx);

    /**
     * 执行下一个中间件
     *
     * @param ctx 上下文
     * @return 执行结果
     */
    protected Object next(HttpContext ctx) {
        if (getNext() == null) {
            return null;
        } else {
            return getNext().invoke(ctx);
        }
    }
}
