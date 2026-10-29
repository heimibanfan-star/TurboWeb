package top.turboweb.concurrent.thread;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * 收尾回调的安全调用点：执行收尾回调并就地消化回调自身抛出的异常，仅记账不扩散。
 * 记账日志自身再抛异常同样消化，保证回调异常的处理不引入新的失败路径。
 */
final class FinallyCallbackGuard {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(FinallyCallbackGuard.class);

    private FinallyCallbackGuard() {
    }

    /**
     * 调用收尾回调：回调与记账日志抛出的异常均就地消化，不向调用方传播。
     *
     * @param finallyHandler 收尾回调
     * @param finallySignal  本次收尾的信号
     */
    static void run(MicroTaskExecutor.FinallyHandler finallyHandler,
                    MicroTaskExecutor.FinallySignal finallySignal) {
        try {
            finallyHandler.onFinally(finallySignal);
        } catch (Throwable callbackFailure) {
            try {
                log.error("finally callback failed", callbackFailure);
            } catch (Throwable ignored) {
                // 记账失败不得引入新的失败路径
            }
        }
    }
}
