package top.turboweb.websocket;

import top.turboweb.concurrent.thread.MicroTaskExecutor;

/**
 * 同步执行提交任务的执行器替身：任务在提交线程立即执行，收尾回调紧随执行结果发出。
 * <p>
 * {@link #accept} 置为 false 时模拟任务未被受理：任务不执行，收尾回调携带
 * {@link MicroTaskExecutor.FinallyType#NOT_ACCEPT} 同步发出。
 */
final class DirectMicroTaskExecutor implements MicroTaskExecutor {

    /** 是否受理提交 */
    boolean accept = true;

    /** 已受理的提交次数，供用例核对分发链路确实经执行器提交 */
    int submissions;

    @Override
    public void submit(Runnable task, FinallyHandler finallyHandler) {
        submissions++;
        if (!accept) {
            finallyHandler.onFinally(
                    new FinallySignal(FinallySignal.CallbackThread.SUBMITTER, FinallyType.NOT_ACCEPT));
            return;
        }
        try {
            task.run();
        } finally {
            finallyHandler.onFinally(
                    new FinallySignal(FinallySignal.CallbackThread.SUBMITTER, FinallyType.EXECUTED));
        }
    }
}
