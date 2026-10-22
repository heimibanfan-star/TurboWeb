package top.turboweb.concurrent.thread;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 虚拟线程微任务执行器抽象：定义向命名虚拟线程提交任务的统一契约。
 * <p>
 * 两种实现不再以独立类型暴露，改由本类以工厂方法产出：{@link #ofUnlimited} 不做资源控制，
 * 每个任务直接进入新建虚拟线程执行，永不因资源受限而未受理；{@link #ofLimited} 以固定资源数
 * 约束并发执行量，资源耗尽时按最大挂起数与最大挂起时间排队，排队失败以未受理情形收尾。每次调用均返回新实例。
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
     * 创建非受限虚拟线程微任务执行器。
     * <p>
     * 不做任何资源控制，每个任务直接进入新建虚拟线程执行：提交即刻受理，不排队，
     * 不会因资源受限而未受理；线程启动失败仍以未受理情形收尾（参数仍须非空，与基类契约一致）。
     * 任务抛出的异常不做拦截，随虚拟线程默认未捕获异常处理器输出；收尾回调仍在任务结束后触发。
     * </p>
     *
     * @param threadName 执行任务所用虚拟线程的名称，必传且不能为 null
     * @return 非受限虚拟线程微任务执行器实例
     * @throws NullPointerException threadName 为 null
     */
    public static VirtualThreadMicroTaskExecutor ofUnlimited(String threadName) {
        return new Unlimited(threadName);
    }

    /**
     * 创建受限虚拟线程微任务执行器：以固定资源数约束并发执行的任务量。
     * <p>
     * 任务获取到资源后在新建虚拟线程中执行，执行结束（含抛出异常）释放资源。
     * 资源耗尽时按最大挂起数与最大挂起时间排队：挂起额度未满的任务在新建虚拟线程中
     * 带超时预算等待资源，等到即执行；等待预算耗尽或等待被中断时放弃，
     * 并在新线程以未受理情形收尾；挂起额度已满或最大挂起时间不大于零时不创建新线程，
     * 直接在当前线程以未受理情形收尾。
     * </p>
     * <p>
     * 挂起额度采用比较并交换（CAS）方式在提交侧条件递增：额度未满原子占位，
     * 超限直接判定失败，计数任何时刻都不会超过最大挂起数，不存在先加后减的回滚写与临时上探窗口；
     * 等待线程离开等待时（无论获取成功、超时还是中断）精确归还额度，并发提交下计数不泄漏。
     * 线程启动失败（含资源耗尽类错误）时，此前占用的许可或挂起额度即时归还后异常原样抛出，
     * 异常路径同样不泄漏资源。
     * </p>
     * <p>
     * 公平模式下资源按进入等待队列的先后顺序交付等待者（先来先处理），
     * 且存在等待者时新提交不会插队占用空闲资源；非公平模式下新提交可立即占用空闲资源。
     * </p>
     *
     * @param resourceCount       资源数，即同时可执行任务的上限，必须大于 0
     * @param maxSuspendThreadNum 最大挂起数，即同时排队等待资源的线程上限，不能小于 0；
     *                            为 0 时资源耗尽即刻在当前线程以未受理情形收尾，不允许排队
     * @param timeout             最大挂起时间（毫秒），单个任务排队等待资源的总预算，不能小于 0；
     *                            为 0 时不排队，资源不足直接在当前线程以未受理情形收尾
     * @param threadName          执行与等待任务所用虚拟线程的名称，必传且不能为 null
     * @param fair                是否公平抢占资源；为 true 时先来先处理，等待队列按先后顺序获得资源，
     *                            且新提交不插队占用空闲资源
     * @return 受限虚拟线程微任务执行器实例
     * @throws IllegalArgumentException 资源数小于等于 0，或最大挂起数、最大挂起时间小于 0
     * @throws NullPointerException     threadName 为 null
     */
    public static VirtualThreadMicroTaskExecutor ofLimited(int resourceCount, int maxSuspendThreadNum, long timeout,
                                                           String threadName, boolean fair) {
        return new Limited(resourceCount, maxSuspendThreadNum, timeout, threadName, fair);
    }

    /**
     * 执行与等待任务所用虚拟线程的名称。
     */
    private final String threadName;

    /**
     * 构造虚拟线程微任务执行器。
     *
     * @param threadName 执行与等待任务所用虚拟线程的名称，必传且不能为 null
     * @throws NullPointerException threadName 为 null
     */
    protected VirtualThreadMicroTaskExecutor(String threadName) {
        this.threadName = Objects.requireNonNull(threadName, "threadName must not be null");
    }

    /**
     * 提交任务执行。
     * <p>
     * 资源不足时的排队与未受理时机由具体实现决定；无论何种实现，
     * 任务与收尾回调均不能为 null，参数校验先于任何资源动作发生。
     * 收尾回调保证被调用且每次提交仅调用一次：任务执行完毕以
     * {@link FinallyType#EXECUTED} 回调；任务未获受理、未启动或排队等待被放弃以
     * {@link FinallyType#NOT_ACCEPT} 回调，回调发生在提交线程或本次提交新建的虚拟线程
     * （由 {@link FinallySignal#callbackThread()} 标识）。
     * </p>
     *
     * @param task           获取到资源后执行的任务，不能为 null
     * @param finallyHandler 任务提交的收尾回调，不能为 null
     * @return 承载任务执行或排队等待的新虚拟线程；实现决定不受理并在当前线程直接以未受理情形收尾时可为 null
     * @throws NullPointerException task 或 finallyHandler 为 null
     */
    public abstract Thread run(Runnable task, FinallyHandler finallyHandler);

    /**
     * 提交任务执行，不返回承载线程。
     * <p>
     * 委托 {@link #run} 执行并丢弃其返回的线程句柄：参数校验、排队与未受理语义、
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
     * 是否受限实现。
     * <p>
     * 受限实现以固定资源数约束并发执行量，资源耗尽时排队或未受理；
     * 非受限实现不做资源控制，每个任务直接执行。
     * 实现已内聚为本类的私有嵌套类型，调用方无法以类型区分实例，以本标志识别。
     * </p>
     *
     * @return 受限实现返回 true，非受限实现返回 false
     */
    public boolean isLimited() {
        return false;
    }

    /**
     * 当前空闲资源数。
     * <p>
     * 受限实现返回可立即分配的资源许可数量，是"还能无条件受理多少个任务"的直接读数；
     * 非受限实现不做资源控制，不设上限，恒返回 {@link Integer#MAX_VALUE}。
     * </p>
     *
     * @return 当前可立即分配的资源数量
     */
    public int availableResources() {
        return Integer.MAX_VALUE;
    }

    /**
     * 当前挂起等待资源的线程数。
     * <p>
     * 受限实现的挂起额度以 CAS 条件递增登记，该读数在任何并发时序下都不超过最大挂起数；
     * 非受限实现不排队，恒返回 0。
     * </p>
     *
     * @return 已登记且尚未结束的等待线程数量
     */
    public int suspendedThreads() {
        return 0;
    }

    /**
     * 是否公平抢占资源。
     * <p>
     * 受限实现返回构造时指定、并由资源许可机制保管的公平标志；
     * 非受限实现无等待队列，公平语义不适用，恒返回 false。
     * </p>
     *
     * @return 受限实现为构造时指定值，非受限实现恒为 false
     */
    public boolean isFair() {
        return false;
    }

    /**
     * 校验 run 的参数契约：任务与收尾回调均不能为 null。
     *
     * @param task           待执行任务
     * @param finallyHandler 收尾回调
     * @throws NullPointerException 任一参数为 null
     */
    protected static void requireArguments(Runnable task, FinallyHandler finallyHandler) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(finallyHandler, "finallyHandler must not be null");
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
                finallyHandler.onFinally(new FinallySignal(FinallySignal.CallbackThread.WORKER, FinallyType.EXECUTED));
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
     * {@link FinallyType#NOT_ACCEPT} 回调收尾，最后原样抛出异常。
     * <p>
     * 调用方在启动前已占用许可或挂起额度时使用本重载：线程创建失败（含资源耗尽类错误）
     * 意味着承载归还逻辑的线程体永远不会运行，故在此即时归还此前占用的资源；
     * 补偿动作与收尾回调各自抛出的异常挂到原异常上，不影响原异常抛出。
     * </p>
     *
     * @param body           线程体
     * @param onStartFailure 启动失败时的补偿动作，归还此前占用的资源
     * @param finallyHandler 收尾回调，启动失败时以 NOT_ACCEPT 回调
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
                finallyHandler.onFinally(new FinallySignal(FinallySignal.CallbackThread.SUBMITTER, FinallyType.NOT_ACCEPT));
            } catch (Throwable callbackFailure) {
                e.addSuppressed(callbackFailure);
            }
            throw e;
        }
    }

    /**
     * 创建并启动配置名称的虚拟线程；启动失败时以
     * {@link FinallyType#NOT_ACCEPT} 回调收尾，最后原样抛出异常。
     * <p>启动前未占用任何资源时使用本重载，启动失败的收尾无需补偿动作。</p>
     *
     * @param body           线程体
     * @param finallyHandler 收尾回调，启动失败时以 NOT_ACCEPT 回调
     * @return 已启动的虚拟线程
     */
    protected Thread startThread(Runnable body, FinallyHandler finallyHandler) {
        return startThread(body, NO_COMPENSATION, finallyHandler);
    }

    /**
     * 非受限实现：不做任何资源控制，每个任务直接进入新建虚拟线程执行。
     * <p>
     * 不排队，不会因资源受限而未受理（线程启动失败仍以未受理情形收尾）；
     * 任务抛出的异常不做拦截，随虚拟线程默认未捕获异常处理器输出，收尾回调仍在任务结束后触发。
     * </p>
     */
    private static final class Unlimited extends VirtualThreadMicroTaskExecutor {

        private Unlimited(String threadName) {
            super(threadName);
        }

        @Override
        public Thread run(Runnable task, FinallyHandler finallyHandler) {
            requireArguments(task, finallyHandler);
            return startThread(withExecuteFinally(task, finallyHandler), finallyHandler);
        }
    }

    /**
     * 受限实现：以固定资源数约束并发执行的任务量。
     * <p>
     * 任务获取到资源后在新建虚拟线程中执行，执行结束（含抛出异常）释放资源；
     * 资源耗尽时按最大挂起数与最大挂起时间排队，排队失败以未受理情形收尾。
     * 挂起额度以 CAS 在提交侧条件递增、离开等待时精确归还，并发提交下计数不泄漏；
     * 线程启动失败时已占用的许可或挂起额度即时归还。
     * </p>
     */
    private static final class Limited extends VirtualThreadMicroTaskExecutor {

        /** 资源许可。 */
        private final Semaphore permission;
        /** 当前挂起等待资源的线程计数。 */
        private final AtomicInteger suspendThreads = new AtomicInteger();
        /** 最大挂起数。 */
        private final int maxSuspendThreadNum;
        /** 最大挂起时间（毫秒）。 */
        private final long timeout;
        /** 启动失败时归还已取得的资源许可。 */
        private final Runnable releasePermitOnStartFailure;

        private Limited(int resourceCount, int maxSuspendThreadNum, long timeout, String threadName, boolean fair) {
            super(threadName);
            if (resourceCount <= 0) {
                throw new IllegalArgumentException("resourceCount must be greater than 0");
            }
            if (maxSuspendThreadNum < 0) {
                throw new IllegalArgumentException("maxSuspendThreadNum must not be negative");
            }
            if (timeout < 0) {
                throw new IllegalArgumentException("timeout must not be negative");
            }
            this.permission = new Semaphore(resourceCount, fair);
            this.maxSuspendThreadNum = maxSuspendThreadNum;
            this.timeout = timeout;
            this.releasePermitOnStartFailure = permission::release;
        }

        @Override
        public Thread run(Runnable task, FinallyHandler finallyHandler) {
            requireArguments(task, finallyHandler);
            if (permission.tryAcquire()) {
                return startThread(() -> executeWithPermit(withExecuteFinally(task, finallyHandler)),
                        releasePermitOnStartFailure, finallyHandler);
            }
            if (timeout <= 0 || !trySuspend()) {
                finallyHandler.onFinally(new FinallySignal(FinallySignal.CallbackThread.SUBMITTER, FinallyType.NOT_ACCEPT));
                return null;
            }
            return startThread(() -> {
                boolean acquired;
                try {
                    acquired = awaitPermission();
                } finally {
                    suspendThreads.decrementAndGet();
                }
                if (acquired) {
                    executeWithPermit(withExecuteFinally(task, finallyHandler));
                } else {
                    finallyHandler.onFinally(new FinallySignal(FinallySignal.CallbackThread.WORKER, FinallyType.NOT_ACCEPT));
                }
            }, suspendThreads::decrementAndGet, finallyHandler);
        }

        /**
         * 持有资源执行任务，执行结束（含抛出异常）后释放资源。
         *
         * @param task 待执行任务
         */
        private void executeWithPermit(Runnable task) {
            try {
                task.run();
            } finally {
                permission.release();
            }
        }

        /**
         * 在挂起状态中限时等待资源，预算为最大挂起时间。
         * <p>
         * 限时获取阻塞至多到预算耗尽：等到返回成功，耗尽仍未等到返回放弃；
         * 等待中被中断同样视为放弃，不保留中断状态（本线程随即结束）。
         * 进入本方法时最大挂起时间必然大于零。
         * </p>
         *
         * @return 是否在预算内成功取得资源
         */
        private boolean awaitPermission() {
            try {
                return permission.tryAcquire(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        /**
         * 以 CAS 条件递增登记一个挂起等待额度。
         * <p>
         * 额度未满时原子递增并返回成功；额度已满时条件函数原值返回，
         * 交换不发生、不产生写入，直接判定失败。计数任何时刻不会超过最大挂起数。
         * </p>
         *
         * @return 登记成功返回 true，额度已满返回 false
         */
        private boolean trySuspend() {
            int previous = suspendThreads.getAndUpdate(current ->
                    current >= maxSuspendThreadNum ? current : current + 1);
            return previous < maxSuspendThreadNum;
        }

        @Override
        public boolean isLimited() {
            return true;
        }

        @Override
        public int availableResources() {
            return permission.availablePermits();
        }

        @Override
        public int suspendedThreads() {
            return suspendThreads.get();
        }

        @Override
        public boolean isFair() {
            return permission.isFair();
        }
    }
}