package top.turboweb.http.testkit;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

/**
 * L1 用例的最小替身集合。
 * <p>
 * {@link #httpContext} 用 {@link Proxy} 实现 {@link HttpContext}：只覆盖本任务被测中间件真正读取的
 * {@code getRequest()} 与 {@code loadValidQuery(...)} 两个入口，其余
 * {@link top.turboweb.http.context.ParamBinder} 方法一律抛 {@link UnsupportedOperationException}——
 * 任何用例若意外走到未覆盖的入口会立刻暴露，而不是静默拿到 {@code null} 让断言失去意义。
 * <p>
 * {@link #threadPool()} 返回不启动任何线程的空实现，仅用于满足
 * {@code ResponseWriterManager} 构造器对非 null 磁盘IO线程池的要求；L1 用例不会真提交任务。
 * <p>
 * L2 用例可要求线程池同步执行：{@link #directExecutorThreadPool()} 在调用线程直接跑完
 * 背压链，{@link #rejectingThreadPool()} / {@link #firstRunRestRejectingThreadPool()}
 * 钉"线程池忙即取消"分支。
 */
public final class Stubs {

    private Stubs() {
    }

    public static HttpContext httpContext(FullHttpRequest request) {
        return httpContext(request, null);
    }

    public static HttpContext httpContext(FullHttpRequest request, Object validQuery) {
        return (HttpContext) Proxy.newProxyInstance(
                HttpContext.class.getClassLoader(),
                new Class<?>[]{HttpContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRequest" -> request;
                    case "loadValidQuery" -> validQuery;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "StubHttpContext";
                    default -> throw new UnsupportedOperationException(
                            "StubHttpContext 未覆盖: " + method.getName());
                });
    }

    public static ThreadPool threadPool() {
        return NoopThreadPool.INSTANCE;
    }

    /** 直接在调用线程跑任务并返回 true：让策略的背压链变成同步可断言。 */
    public static ThreadPool directExecutorThreadPool() {
        return Direct.INSTANCE;
    }

    /** execute 恒返 false：钉策略"线程池忙即取消"分支。 */
    public static ThreadPool rejectingThreadPool() {
        return Rejecting.INSTANCE;
    }

    /** 第一个任务同步跑，其后一律拒绝：只发得出一块然后走 cancel 分支。 */
    public static ThreadPool firstRunRestRejectingThreadPool() {
        return new FirstThenReject();
    }

    private enum Direct implements ThreadPool {
        INSTANCE;
        @Override public boolean execute(Runnable task) { task.run(); return true; }
        @Override public void shutdown() { }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    private enum Rejecting implements ThreadPool {
        INSTANCE;
        @Override public boolean execute(Runnable task) { return false; }
        @Override public void shutdown() { }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    private static final class FirstThenReject implements ThreadPool {
        private boolean used = false;
        @Override public synchronized boolean execute(Runnable task) {
            if (!used) { used = true; task.run(); return true; }
            return false;
        }
        @Override public void shutdown() { }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    /** 不启动线程、不接受任务的空线程池，仅用于构造需要非 null 线程池的策略对象。 */
    private enum NoopThreadPool implements ThreadPool {
        INSTANCE;

        @Override
        public boolean execute(Runnable task) {
            throw new UnsupportedOperationException("L1 用例不应向线程池提交任务");
        }

        @Override
        public void shutdown() {
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
