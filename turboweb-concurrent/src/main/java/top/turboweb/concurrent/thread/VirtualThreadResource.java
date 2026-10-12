package top.turboweb.concurrent.thread;

import java.util.Objects;

/**
 * 虚拟线程资源抽象：定义向命名虚拟线程提交任务的统一契约。
 * <p>
 * 子类为两种实现：{@link LimitedVirtualThreadResource} 以固定资源数约束并发执行量，
 * 资源耗尽时按最大挂起数与最大挂起时间排队，排队失败触发拒绝回调；
 * {@link UnlimitedVirtualThreadResource} 不做资源控制，每个任务直接进入新建虚拟线程执行，永不拒绝。
 * </p>
 * <p>
 * 线程名为必传参数，构造时校验非空，供两个实现创建执行与等待任务所用虚拟线程时命名；
 * run 的参数契约（任务与拒绝回调均不能为 null、校验先于任何资源动作）由基类统一承载。
 * </p>
 */
public abstract class VirtualThreadResource {

    /**
     * 拒绝回调发生的线程标识。
     */
    public enum RejectionThread {
        /**
         * 拒绝在当前线程同步回调：挂起额度已满或最大挂起时间不大于零，未创建新线程。
         */
        CURRENT_THREAD,
        /**
         * 拒绝在新创建的虚拟线程回调：排队等待超时预算耗尽，或等待被中断。
         */
        NEW_THREAD
    }

    /**
     * 任务获取资源失败被拒绝时的回调。
     */
    @FunctionalInterface
    public interface RejectionHandler {

        /**
         * 任务被拒绝时调用。
         *
         * @param rejectionThread 本次回调发生的线程标识
         */
        void onRejected(RejectionThread rejectionThread);
    }

    /**
     * 执行与等待任务所用虚拟线程的名称。
     */
    private final String threadName;

    /**
     * 构造虚拟线程资源。
     *
     * @param threadName 执行与等待任务所用虚拟线程的名称，必传且不能为 null
     * @throws NullPointerException threadName 为 null
     */
    protected VirtualThreadResource(String threadName) {
        this.threadName = Objects.requireNonNull(threadName, "线程名不能为null");
    }

    /**
     * 提交任务执行。
     * <p>
     * 资源不足时的排队与拒绝语义由具体实现决定；无论何种实现，
     * 任务与拒绝回调均不能为 null，参数校验先于任何资源动作发生。
     * </p>
     *
     * @param task             获取到资源后执行的任务，不能为 null
     * @param rejectionHandler 任务被拒绝时的回调，不能为 null
     * @return 承载任务执行或排队等待的新虚拟线程；实现决定不受理并在当前线程直接拒绝时可为 null
     * @throws NullPointerException task 或 rejectionHandler 为 null
     */
    public abstract Thread run(Runnable task, RejectionHandler rejectionHandler);

    /**
     * 校验 run 的参数契约：任务与拒绝回调均不能为 null。
     *
     * @param task             待执行任务
     * @param rejectionHandler 拒绝回调
     * @throws NullPointerException 任一参数为 null
     */
    protected static void requireArguments(Runnable task, RejectionHandler rejectionHandler) {
        Objects.requireNonNull(task, "执行任务不能为null");
        Objects.requireNonNull(rejectionHandler, "拒绝回调不能为null");
    }

    /**
     * 创建并启动配置名称的虚拟线程。
     *
     * @param body 线程体
     * @return 已启动的虚拟线程
     */
    protected Thread startThread(Runnable body) {
        return Thread.ofVirtual().name(threadName).start(body);
    }

    /**
     * 创建并启动配置名称的虚拟线程；启动失败时先执行补偿动作再原样抛出异常。
     * <p>
     * 调用方在启动前已占用许可或挂起额度时使用本重载：线程创建失败（含资源耗尽类错误）
     * 意味着承载归还逻辑的线程体永远不会运行，故在此即时归还此前占用的资源，
     * 保证异常路径同样不泄漏许可与额度。
     * </p>
     *
     * @param body           线程体
     * @param onStartFailure 启动失败时的补偿动作，归还此前占用的资源
     * @return 已启动的虚拟线程
     */
    protected Thread startThread(Runnable body, Runnable onStartFailure) {
        try {
            return startThread(body);
        } catch (RuntimeException | Error e) {
            onStartFailure.run();
            throw e;
        }
    }
}
