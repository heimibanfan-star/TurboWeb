package top.turboweb.concurrent.thread;

import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * ForkJoin 微任务执行器：以工作窃取的任务池承载提交的小粒度任务。
 * <p>
 * 共享实例经 {@link #ofShared()} 产出，为双重检查锁懒加载的全局单例，线程数取默认
 * 并行度（CPU 核心数减一，最小为一），工作线程名前缀为 forkjoin-shared-micro-task；
 * 隔离实例经 {@link #ofIsolated} 产出，每次调用均新建独立任务池，线程数可显式指定，
 * 也可只传名称按默认并行度创建，工作线程按所传名称加序号命名。工作线程在池内复用，
 * 不为单个任务新建线程。
 * </p>
 * <p>
 * 实现 {@link MicroTaskExecutor}：任务与收尾回调均不能为 null，参数校验先于任何池动作；
 * 任务被池受理并执行完毕（正常返回或抛出异常）以 {@link FinallyType#EXECUTED} 收尾，
 * 任务抛出的异常不向提交方传播，工作线程不受影响；提交任务未获受理、任务未启动
 * （任务池已终止拒绝受理，或受理阶段抛出 {@link RuntimeException} 或 {@link Error}，
 * 典型为内存不足导致的受理分配失败）时，以 {@link FinallyType#NOT_ACCEPT} 收尾，
 * 原异常或错误在收尾后继续向提交方抛出。池内工作线程为驻留线程，不存在逐任务启动
 * 线程的路径：任务要么已被受理并执行，要么根本未启动，故本实现只产生
 * {@link FinallyType#EXECUTED} 与 {@link FinallyType#NOT_ACCEPT} 两种收尾。
 * </p>
 */
public final class ForkJoinMicroTaskExecutor implements MicroTaskExecutor {

    /** 共享实例的工作线程名称前缀。 */
    private static final String SHARED_THREAD_NAME = "forkjoin-shared-micro-task";

    /** 共享单例，volatile 使双重检查锁下的首次可见性成立。 */
    private static volatile ForkJoinMicroTaskExecutor shared;

    /** 承载并调度任务的工作窃取池。 */
    private final ForkJoinPool pool;

    private ForkJoinMicroTaskExecutor(int parallelism, String threadNamePrefix) {
        this.pool = new ForkJoinPool(parallelism, namedWorkerFactory(threadNamePrefix), null, true);
    }

    /**
     * 获取共享 ForkJoin 微任务执行器。
     * <p>
     * 全局唯一的懒加载单例：首次调用以双重检查锁创建，后续调用直接返回同一实例；
     * 并行度取默认值（CPU 核心数减一，最小为一）。
     * </p>
     *
     * @return 共享 ForkJoin 微任务执行器
     */
    public static ForkJoinMicroTaskExecutor ofShared() {
        ForkJoinMicroTaskExecutor instance = shared;
        if (instance == null) {
            synchronized (ForkJoinMicroTaskExecutor.class) {
                instance = shared;
                if (instance == null) {
                    instance = shared = new ForkJoinMicroTaskExecutor(defaultParallelism(), SHARED_THREAD_NAME);
                }
            }
        }
        return instance;
    }

    /**
     * 创建隔离 ForkJoin 微任务执行器：独立任务池，并行度与工作线程名前缀按给定值。
     * <p>每次调用均新建独立池与工作线程，互不影响。</p>
     *
     * @param threadName  工作线程名称前缀，必传且不能为 null
     * @param threadCount 并行度，即同时运行的工作线程数上限，必须大于 0
     * @return 新的隔离 ForkJoin 微任务执行器
     * @throws NullPointerException     threadName 为 null
     * @throws IllegalArgumentException threadCount 小于等于 0
     */
    public static ForkJoinMicroTaskExecutor ofIsolated(String threadName, int threadCount) {
        Objects.requireNonNull(threadName, "threadName must not be null");
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount must be greater than 0");
        }
        return new ForkJoinMicroTaskExecutor(threadCount, threadName);
    }

    /**
     * 创建隔离 ForkJoin 微任务执行器：只给工作线程名称前缀，并行度取默认值。
     * <p>与双参数重载的差异仅在并行度来源：本方法按默认并行度（CPU 核心数减一，最小为一）创建。</p>
     *
     * @param threadName 工作线程名称前缀，必传且不能为 null
     * @return 新的隔离 ForkJoin 微任务执行器
     * @throws NullPointerException threadName 为 null
     */
    public static ForkJoinMicroTaskExecutor ofIsolated(String threadName) {
        Objects.requireNonNull(threadName, "threadName must not be null");
        return new ForkJoinMicroTaskExecutor(defaultParallelism(), threadName);
    }

    /**
     * 提交任务执行，不返回承载线程。
     * <p>
     * 任务被池受理后由命名工作线程执行，执行结束（正常返回或抛出异常）以
     * {@link FinallyType#EXECUTED} 回调收尾，回调在执行任务的线程中触发；
     * 提交任务未获受理、任务未启动（任务池已终止拒绝受理，或受理阶段抛出
     * {@link RuntimeException} 或 {@link Error}，典型为内存不足）时，以
     * {@link FinallyType#NOT_ACCEPT} 回调收尾，原异常或错误在收尾后继续向提交方抛出。
     * 任务池承接任务执行期抛出的异常，不向提交方传播，也不影响工作线程后续受理。
     * 任务与收尾回调均不能为 null，校验先于任何池动作，收尾回调每次提交仅调用一次。
     * </p>
     *
     * @param task           获取到资源后执行的任务，不能为 null
     * @param finallyHandler 任务提交的收尾回调，不能为 null
     * @throws NullPointerException task 或 finallyHandler 为 null
     * @throws RuntimeException 受理阶段抛出的运行时异常（含任务池已终止的拒绝受理），NOT_ACCEPT 收尾后继续抛出
     */
    @Override
    public void submit(Runnable task, FinallyHandler finallyHandler) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(finallyHandler, "finallyHandler must not be null");
        try {
            pool.execute(withExecuteFinally(task, finallyHandler));
        } catch (RuntimeException | Error unstarted) {
            finallyHandler.onFinally(new FinallySignal(FinallySignal.CallbackThread.SUBMITTER, FinallyType.NOT_ACCEPT));
            throw unstarted;
        }
    }

    /**
     * 当前执行器的并行度，即同时运行的工作线程数上限。
     *
     * @return 构造时指定的并行度；共享实例与单名称重载为默认并行度
     */
    int parallelism() {
        return pool.getParallelism();
    }

    /**
     * 返回命名工作线程的工厂，线程以 前缀-序号 命名。
     * <p>任务池注册工作线程时会把默认命名覆盖到线程上，故重命名放在线程启动钩子
     * {@link NamedWorkerThread#onStart()}，注册完成后生效。</p>
     *
     * @param threadNamePrefix 工作线程名称前缀
     * @return 命名工作线程工厂
     */
    private static ForkJoinWorkerThreadFactory namedWorkerFactory(String threadNamePrefix) {
        return targetPool -> new NamedWorkerThread(targetPool, threadNamePrefix);
    }

    /**
     * 包装任务：任务执行完毕（正常返回或抛出异常）后，在执行任务的线程以
     * {@link FinallyType#EXECUTED} 回调收尾。
     *
     * @param task           待执行任务
     * @param finallyHandler 收尾回调
     * @return 带执行完毕收尾通知的任务
     */
    private static Runnable withExecuteFinally(Runnable task, FinallyHandler finallyHandler) {
        return () -> {
            try {
                task.run();
            } finally {
                finallyHandler.onFinally(new FinallySignal(FinallySignal.CallbackThread.WORKER, FinallyType.EXECUTED));
            }
        };
    }

    /**
     * 默认并行度：CPU 核心数减一，最小为一。
     *
     * @return 默认并行度
     */
    private static int defaultParallelism() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    /**
     * 带名称前缀的工作线程：启动钩子中把线程名改写为 前缀-序号。
     */
    private static final class NamedWorkerThread extends ForkJoinWorkerThread {

        private final String threadNamePrefix;

        private NamedWorkerThread(ForkJoinPool targetPool, String threadNamePrefix) {
            super(targetPool);
            this.threadNamePrefix = threadNamePrefix;
        }

        @Override
        protected void onStart() {
            setName(threadNamePrefix + "-" + getPoolIndex());
            super.onStart();
        }
    }
}