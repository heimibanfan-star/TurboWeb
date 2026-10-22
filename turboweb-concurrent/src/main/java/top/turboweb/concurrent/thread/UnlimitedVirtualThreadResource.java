package top.turboweb.concurrent.thread;

/**
 * 非受限虚拟线程资源：不做任何资源控制，每个任务直接进入新建虚拟线程执行。
 * <p>
 * 提交即刻受理，不排队、不拒绝，收尾回调不会以拒绝情形触发（参数仍须非空，与基类契约一致）。
 * 任务抛出的异常不做拦截，随虚拟线程默认未捕获异常处理器输出；收尾回调仍在任务结束后触发。
 * </p>
 */
public final class UnlimitedVirtualThreadResource extends VirtualThreadMicroTaskExecutor {

    /**
     * 构造非受限虚拟线程资源。
     *
     * @param threadName 执行任务所用虚拟线程的名称，必传且不能为 null
     * @throws NullPointerException threadName 为 null
     */
    public UnlimitedVirtualThreadResource(String threadName) {
        super(threadName);
    }

    /**
     * 提交任务执行：任务在新建虚拟线程中运行，永不拒绝。
     *
     * @param task           执行任务，不能为 null
     * @param finallyHandler 收尾回调，不能为 null；任务执行完毕以 EXECUTED 回调，
     *                       线程未能启动以 NOT_STARTED 回调，本实现不会以 REJECTED 回调
     * @return 承载任务执行的新虚拟线程，永不为 null
     * @throws NullPointerException task 或 finallyHandler 为 null
     */
    @Override
    public Thread run(Runnable task, FinallyHandler finallyHandler) {
        requireArguments(task, finallyHandler);
        return startThread(withExecuteFinally(task, finallyHandler), finallyHandler);
    }
}
