package top.turboweb.concurrent.thread;

/**
 * 非受限虚拟线程资源：不做任何资源控制，每个任务直接进入新建虚拟线程执行。
 * <p>
 * 提交即刻受理，不排队、不拒绝，拒绝回调永不触发（参数仍须非空，与基类契约一致）。
 * 任务抛出的异常不做拦截，随虚拟线程默认未捕获异常处理器输出。
 * </p>
 */
public final class UnlimitedVirtualThreadResource extends VirtualThreadResource {

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
     * @param task             执行任务，不能为 null
     * @param rejectionHandler 拒绝回调，不能为 null；本实现不会调用
     * @return 承载任务执行的新虚拟线程，永不为 null
     * @throws NullPointerException task 或 rejectionHandler 为 null
     */
    @Override
    public Thread run(Runnable task, RejectionHandler rejectionHandler) {
        requireArguments(task, rejectionHandler);
        return startThread(task);
    }
}
