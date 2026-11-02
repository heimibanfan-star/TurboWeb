package top.turboweb.http.testkit;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

/**
 * L1 用例的最小替身集合。
 * <p>
 * {@link #httpContext} 用 {@link Proxy} 实现 {@link HttpContext}：只覆盖本任务被测中间件真正读取的
 * {@code message()} 入口（按编排器装配口径从请求建立），其余方法一律抛 {@link UnsupportedOperationException}——
 * 任何用例若意外走到未覆盖的入口会立刻暴露，而不是静默拿到 {@code null} 让断言失去意义。
 * <p>
 * L2 用例可要求执行器同步执行：{@link #directExecutor()} 在调用线程直接跑完
 * 提交链，{@link #rejectingExecutor()} / {@link #firstRunRestRejectingExecutor()}
 * 钉"执行器忙即取消"分支。
 */
public final class Stubs {

    private Stubs() {
    }

    public static HttpContext httpContext(FullHttpRequest request) {
        return (HttpContext) Proxy.newProxyInstance(
                HttpContext.class.getClassLoader(),
                new Class<?>[]{HttpContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "message" -> Contexts.message(request, StandardCharsets.UTF_8);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "StubHttpContext";
                    default -> throw new UnsupportedOperationException(
                            "StubHttpContext 未覆盖: " + method.getName());
                });
    }

    /** 直接在调用线程跑任务并以已执行收尾：让策略的提交链变成同步可断言。 */
    public static MicroTaskExecutor directExecutor() {
        return Direct.INSTANCE;
    }

    /** 提交一律以未受理收尾：钉策略"执行器忙即取消"分支。 */
    public static MicroTaskExecutor rejectingExecutor() {
        return Rejecting.INSTANCE;
    }

    /** 第一个任务同步跑，其后一律未受理：只发得出一块然后走 cancel 分支。 */
    public static MicroTaskExecutor firstRunRestRejectingExecutor() {
        return new FirstThenReject();
    }

    /** 同步执行器：任务在调用线程跑完，收尾在任务结束后以工作线程方已执行触发。 */
    private enum Direct implements MicroTaskExecutor {
        INSTANCE;
        @Override
        public void submit(Runnable task, FinallyHandler finallyHandler) {
            try {
                task.run();
            } finally {
                finallyHandler.onFinally(new FinallySignal(
                        FinallySignal.CallbackThread.WORKER, FinallyType.EXECUTED));
            }
        }
    }

    /** 拒绝型执行器：不执行任务，在提交线程方以未受理收尾。 */
    private enum Rejecting implements MicroTaskExecutor {
        INSTANCE;
        @Override
        public void submit(Runnable task, FinallyHandler finallyHandler) {
            finallyHandler.onFinally(new FinallySignal(
                    FinallySignal.CallbackThread.SUBMITTER, FinallyType.NOT_ACCEPT));
        }
    }

    /** 首次同步执行、其后一律未受理的执行器。 */
    private static final class FirstThenReject implements MicroTaskExecutor {
        private boolean used = false;
        @Override
        public synchronized void submit(Runnable task, FinallyHandler finallyHandler) {
            if (!used) {
                used = true;
                try {
                    task.run();
                } finally {
                    finallyHandler.onFinally(new FinallySignal(
                            FinallySignal.CallbackThread.WORKER, FinallyType.EXECUTED));
                }
                return;
            }
            finallyHandler.onFinally(new FinallySignal(
                    FinallySignal.CallbackThread.SUBMITTER, FinallyType.NOT_ACCEPT));
        }
    }
}
