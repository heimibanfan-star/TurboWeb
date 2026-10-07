package top.turboweb.http.testkit;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.commons.utils.thread.ThreadPool;
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
 * {@code FileStreamResponseStrategy} 构造器对非 null 线程池的要求；L1 用例不会真提交任务。
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
