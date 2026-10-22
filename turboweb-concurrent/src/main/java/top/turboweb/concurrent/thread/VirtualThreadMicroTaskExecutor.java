package top.turboweb.concurrent.thread;

import java.util.Objects;

/**
 * 虚拟线程资源抽象：定义向命名虚拟线程提交任务的统一契约。
 * <p>
 * 子类为两种实现：{@link LimitedVirtualThreadResource} 以固定资源数约束并发执行量，
 * 资源耗尽时按最大挂起数与最大挂起时间排队，排队失败以拒绝情形收尾；
 * {@link UnlimitedVirtualThreadResource} 不做资源控制，每个任务直接进入新建虚拟线程执行，永不拒绝。
 * </p>
 * <p>
 * 线程名为必传参数，构造时校验非空，供两个实现创建执行与等待任务所用虚拟线程时命名；
 * run 的参数契约（任务与收尾回调均不能为 null、校验先于任何资源动作）由基类统一承载；
 * 本类实现 {@link MicroTaskExecutor}，submit 委托 run 执行并丢弃其线程返回值。
 * </p>
 */
public abstract class VirtualThreadMicroTaskExecutor implements MicroTaskExecutor {

    /** 无补偿动作的启动失败收尾：线程创建前未占用任何资源。 */
    private static final Runnable NO_COMPENSATION = () -> {
    };

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
    protected VirtualThreadMicroTaskExecutor(String threadName) {
        this.threadName = Objects.requireNonNull(threadName, "线程名不能为null");
    }

    /**
     * 提交任务执行。
     * <p>
     * 资源不足时的排队与拒绝语义由具体实现决定；无论何种实现，
     * 任务与收尾回调均不能为 null，参数校验先于任何资源动作发生。
     * 收尾回调保证被调用且每次提交仅调用一次：任务执行完毕以
     * {@link FinallyType#EXECUTED} 回调，未取得资源以 {@link FinallyType#REJECTED} 回调，
     * 虚拟线程未能启动以 {@link FinallyType#NOT_STARTED} 回调。
     * </p>
     *
     * @param task           获取到资源后执行的任务，不能为 null
     * @param finallyHandler 任务提交的收尾回调，不能为 null
     * @return 承载任务执行或排队等待的新虚拟线程；实现决定不受理并在当前线程直接拒绝时可为 null
     * @throws NullPointerException task 或 finallyHandler 为 null
     */
    public abstract Thread run(Runnable task, FinallyHandler finallyHandler);

    /**
     * 提交任务执行，不返回承载线程。
     * <p>
     * 委托 {@link #run} 执行并丢弃其返回的线程句柄：参数校验、排队与拒绝语义、
     * 收尾回调时机均与 run 一致。
     * </p>
     *
     * @param task           获取到资源后执行的任务，不能为 null
     * @param finallyHandler 任务提交的收尾回调，不能为 null
     * @throws NullPointerException task 或 finallyHandler 为 null
     */
    @Override
    public void submit(Runnable task, FinallyHandler finallyHandler) {
        run(task, finallyHandler);
    }

    /**
     * 校验 run 的参数契约：任务与收尾回调均不能为 null。
     *
     * @param task           待执行任务
     * @param finallyHandler 收尾回调
     * @throws NullPointerException 任一参数为 null
     */
    protected static void requireArguments(Runnable task, FinallyHandler finallyHandler) {
        Objects.requireNonNull(task, "执行任务不能为null");
        Objects.requireNonNull(finallyHandler, "收尾回调不能为null");
    }

    /**
     * 包装任务：任务执行完毕（正常返回或抛出异常）后，在任务所在线程以
     * {@link FinallyType#EXECUTED} 回调收尾。
     *
     * @param task           待执行任务
     * @param finallyHandler 收尾回调
     * @return 带执行完毕收尾通知的任务
     */
    protected static Runnable withExecuteFinally(Runnable task, FinallyHandler finallyHandler) {
        return () -> {
            try {
                task.run();
            } finally {
                finallyHandler.onFinally(FinallyType.EXECUTED);
            }
        };
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
     * 创建并启动配置名称的虚拟线程；启动失败时先执行补偿动作，再以
     * {@link FinallyType#NOT_STARTED} 回调收尾，最后原样抛出异常。
     * <p>
     * 调用方在启动前已占用许可或挂起额度时使用本重载：线程创建失败（含资源耗尽类错误）
     * 意味着承载归还逻辑的线程体永远不会运行，故在此即时归还此前占用的资源；
     * 补偿动作与收尾回调各自抛出的异常挂到原异常上，不影响原异常抛出。
     * </p>
     *
     * @param body           线程体
     * @param onStartFailure 启动失败时的补偿动作，归还此前占用的资源
     * @param finallyHandler 收尾回调，启动失败时以 NOT_STARTED 回调
     * @return 已启动的虚拟线程
     */
    protected Thread startThread(Runnable body, Runnable onStartFailure, FinallyHandler finallyHandler) {
        try {
            return startThread(body);
        } catch (RuntimeException | Error e) {
            try {
                onStartFailure.run();
            } catch (Throwable compensationFailure) {
                e.addSuppressed(compensationFailure);
            }
            try {
                finallyHandler.onFinally(FinallyType.NOT_STARTED);
            } catch (Throwable callbackFailure) {
                e.addSuppressed(callbackFailure);
            }
            throw e;
        }
    }

    /**
     * 创建并启动配置名称的虚拟线程；启动失败时以
     * {@link FinallyType#NOT_STARTED} 回调收尾，最后原样抛出异常。
     *
     * @param body           线程体
     * @param finallyHandler 收尾回调，启动失败时以 NOT_STARTED 回调
     * @return 已启动的虚拟线程
     */
    protected Thread startThread(Runnable body, FinallyHandler finallyHandler) {
        return startThread(body, NO_COMPENSATION, finallyHandler);
    }
}
