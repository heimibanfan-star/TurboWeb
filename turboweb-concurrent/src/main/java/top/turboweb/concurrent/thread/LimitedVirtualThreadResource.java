package top.turboweb.concurrent.thread;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 受限虚拟线程资源：以固定资源数约束并发执行的任务量。
 * <p>
 * 任务获取到资源后在新建虚拟线程中执行，执行结束（含抛出异常）释放资源。
 * 资源耗尽时按最大挂起数与最大挂起时间排队：挂起额度未满的任务在新建虚拟线程中
 * 带超时预算等待资源，等到即执行；等待预算耗尽或等待被中断时放弃，
 * 并在新线程以拒绝情形收尾；挂起额度已满或最大挂起时间不大于零时不创建新线程，
 * 直接在当前线程以拒绝情形收尾。
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
 */
public final class LimitedVirtualThreadResource extends VirtualThreadMicroTaskExecutor {

    /**
     * 资源许可。
     */
    private final Semaphore permission;
    /**
     * 当前挂起等待资源的线程计数。
     */
    private final AtomicInteger suspendThreads = new AtomicInteger();
    /**
     * 最大挂起数。
     */
    private final int maxSuspendThreadNum;
    /**
     * 最大挂起时间（毫秒）。
     */
    private final long timeout;
    /**
     * 启动失败时归还已取得的资源许可。
     */
    private final Runnable releasePermitOnStartFailure;

    /**
     * 构造受限虚拟线程资源。
     *
     * @param resourceCount       资源数，即同时可执行任务的上限，必须大于 0
     * @param maxSuspendThreadNum 最大挂起数，即同时排队等待资源的线程上限，不能小于 0；
     *                            为 0 时资源耗尽即刻在当前线程拒绝，不允许排队
     * @param timeout             最大挂起时间（毫秒），单个任务排队等待资源的总预算，不能小于 0；
     *                            为 0 时不排队，资源不足直接在当前线程拒绝
     * @param threadName          执行与等待任务所用虚拟线程的名称，必传且不能为 null
     * @param fair                是否公平抢占资源；为 true 时先来先处理，等待队列按先后顺序获得资源，
     *                            且新提交不插队占用空闲资源
     * @throws IllegalArgumentException 资源数小于等于 0，或最大挂起数、最大挂起时间小于 0
     * @throws NullPointerException     threadName 为 null
     */
    public LimitedVirtualThreadResource(int resourceCount, int maxSuspendThreadNum, long timeout, String threadName, boolean fair) {
        super(threadName);
        if (resourceCount <= 0) {
            throw new IllegalArgumentException("资源数必须大于0");
        }
        if (maxSuspendThreadNum < 0) {
            throw new IllegalArgumentException("最大挂起数不能小于0");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("最大挂起时间不能小于0");
        }
        this.permission = new Semaphore(resourceCount, fair);
        this.maxSuspendThreadNum = maxSuspendThreadNum;
        this.timeout = timeout;
        this.releasePermitOnStartFailure = permission::release;
    }

    /**
     * 提交任务执行。
     * <p>
     * 资源空闲时立即取得资源，任务在新建虚拟线程中执行，执行结束（含抛出异常）后释放资源，
     * 收尾回调以 {@link FinallyType#EXECUTED} 触发；
     * 资源耗尽且允许排队时，新建虚拟线程在最大挂起时间预算内限时等待，等到资源即执行任务，
     * 预算耗尽或被中断时放弃等待并在等待线程以 {@link FinallyType#REJECTED} 收尾；
     * 最大挂起时间不大于零或挂起额度已满时不创建新线程，
     * 直接在当前线程以 {@link FinallyType#REJECTED} 收尾并返回 null。
     * </p>
     * <p>
     * 任务抛出的异常不做拦截，随虚拟线程默认未捕获异常处理器输出，资源照常释放；
     * 对返回的等待线程调用 {@link Thread#interrupt()} 可使其放弃排队并按拒绝情形收尾；
     * 新线程创建失败（含资源耗尽类错误）时，已占用的许可或挂起额度即时归还，
     * 收尾回调以 {@link FinallyType#NOT_STARTED} 触发后异常原样抛出。
     * </p>
     *
     * @param task           获取到资源后执行的任务，不能为 null
     * @param finallyHandler 任务提交的收尾回调，不能为 null
     * @return 承载任务执行或排队等待的新虚拟线程；在当前线程直接拒绝时为 null
     * @throws NullPointerException task 或 finallyHandler 为 null
     */
    @Override
    public Thread run(Runnable task, FinallyHandler finallyHandler) {
        requireArguments(task, finallyHandler);
        if (permission.tryAcquire()) {
            return startThread(() -> executeWithPermit(withExecuteFinally(task, finallyHandler)),
                    releasePermitOnStartFailure, finallyHandler);
        }
        if (timeout <= 0 || !trySuspend()) {
            finallyHandler.onFinally(FinallyType.REJECTED);
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
                finallyHandler.onFinally(FinallyType.REJECTED);
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

    /**
     * 当前空闲资源数。
     *
     * @return 可立即分配的资源许可数量
     */
    public int availableResources() {
        return permission.availablePermits();
    }

    /**
     * 当前挂起等待资源的线程数。
     * <p>额度以 CAS 条件递增登记，该读数在任何并发时序下都不超过最大挂起数。</p>
     *
     * @return 已登记且尚未结束的等待线程数量
     */
    public int suspendedThreads() {
        return suspendThreads.get();
    }

    /**
     * 是否公平抢占资源。
     *
     * @return 构造时指定公平则为 true
     */
    public boolean isFair() {
        return permission.isFair();
    }
}
