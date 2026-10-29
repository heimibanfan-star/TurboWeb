package top.turboweb.concurrent.thread;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 弹性微任务执行器，实现 {@link MicroTaskExecutor}：以固定上限的平台线程承载虚拟线程无法卸载的
 * IO阻塞型任务（如磁盘读写），避免此类任务长期占住虚拟线程的载体线程。
 * <p>
 * 由核心执行器 {@link ThreadPoolExecutor} 与一个二级缓冲队列 {@code cacheQueue} 组成，
 * 另有一条虚拟哨兵线程负责把缓冲队列中的任务搬运进执行器：
 * <ul>
 *   <li>核心线程数固定为1，核心队列（{@link ArrayBlockingQueue}）积压时按最大线程数扩容，
 *   空闲线程按存活时间回收（核心线程同样允许超时回收），即"弹性"所在；</li>
 *   <li>核心队列满时任务转入缓冲队列，由哨兵线程搬运，从而在突发流量下平滑削峰而不丢任务；</li>
 *   <li>搬运时执行器若仍拒绝（核心队列依旧满），按指数退避重试，避免空转。</li>
 * </ul>
 * <p>
 * 收尾语义：任务受理后执行完毕（正常返回或抛出异常）在承载执行的工作线程方以
 * {@link MicroTaskExecutor.FinallyType#EXECUTED} 收尾；核心队列与缓冲队列皆满、或执行器已关闭时，
 * 在提交线程方以 {@link MicroTaskExecutor.FinallyType#NOT_ACCEPT} 收尾，不抛异常。
 * <p>
 * 本类以<b>实例</b>为使用单位：队列、执行器与搬运线程均归属各实例，不共享全局任务状态，
 * 构造即完成初始化，参数由调用方经构造器指定，同一JVM内可并存多个互不干扰的执行器。
 *
 * <pre>{@code
 * MicroTaskExecutor executor = new ElasticMicroTaskExecutor(
 *         1024,   // 缓冲队列容量
 *         2,      // 核心队列容量
 *         10      // 最大线程数
 * );
 * executor.submit(() -> doDiskIo(), signal -> { });
 * executor.close();
 * }</pre>
 */
public class ElasticMicroTaskExecutor implements MicroTaskExecutor {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ElasticMicroTaskExecutor.class);

    /** 执行器组编号，用于区分同一JVM内创建的多个实例 */
    private static final AtomicInteger GROUP_ID = new AtomicInteger(0);

    /** 搬运重试的初始退避时间（纳秒） */
    private static final long INIT_BACKOFF_NANOS = 1000L;

    /** 搬运重试的退避上限（纳秒） */
    private static final long MAX_BACKOFF_NANOS = 16_384_000L;

    /** 关闭时等待哨兵线程退出的最长时间 */
    private static final long MOVER_JOIN_TIMEOUT_MILLIS = 1000L;

    private static class ElasticThreadFactory implements ThreadFactory {

        private final AtomicLong threadId = new AtomicLong(0);
        private final int groupId;

        public ElasticThreadFactory(int groupId) {
            this.groupId = groupId;
        }

        @Override
        public Thread newThread(@NonNull Runnable r) {
            threadId.compareAndSet(Long.MAX_VALUE, 0);
            Thread thread = new Thread(r, "ElasticMicroTaskExecutor-" + groupId + "-" + threadId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    /** 任务缓冲队列：核心队列满后的二级缓冲 */
    private final BlockingQueue<Runnable> cacheQueue;

    /** 核心执行器 */
    private final ThreadPoolExecutor threadPoolExecutor;

    /** 搬运线程（虚拟线程），负责把缓冲队列中的任务搬进核心执行器 */
    private final Thread taskMoveThread;

    /**
     * 构造弹性微任务执行器。
     * <p>
     * 内部固定：核心线程1个、最大线程数 {@code maxThreadNum}、存活时间5分钟、核心队列为
     * {@link ArrayBlockingQueue}（容量 {@code coreQueue}）、拒绝策略 {@link ThreadPoolExecutor.AbortPolicy}、
     * 核心线程允许超时回收；缓冲队列为 {@link LinkedBlockingQueue}（容量 {@code cacheQueue}）。
     *
     * @param cacheQueue   缓冲队列容量，必须大于0
     * @param coreQueue    核心队列容量，必须大于0
     * @param maxThreadNum 最大线程数，必须大于0
     * @throws IllegalArgumentException 任一参数不大于0
     */
    public ElasticMicroTaskExecutor(int cacheQueue, int coreQueue, int maxThreadNum) {
        if (cacheQueue <= 0) {
            throw new IllegalArgumentException("cacheQueue must be greater than 0");
        }
        if (coreQueue <= 0) {
            throw new IllegalArgumentException("coreQueue must be greater than 0");
        }
        if (maxThreadNum <= 0) {
            throw new IllegalArgumentException("maxThreadNum must be greater than 0");
        }
        int groupId = GROUP_ID.getAndIncrement();
        this.threadPoolExecutor = new ThreadPoolExecutor(
                1,
                maxThreadNum,
                5,
                TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(coreQueue),
                new ElasticThreadFactory(groupId),
                new ThreadPoolExecutor.AbortPolicy()
        );
        // 核心线程同样允许超时回收，空闲时线程数可回落到0
        this.threadPoolExecutor.allowCoreThreadTimeOut(true);
        this.cacheQueue = new LinkedBlockingQueue<>(cacheQueue);
        this.taskMoveThread = Thread.ofVirtual()
                .name("ElasticMicroTaskExecutor-Mover-" + groupId)
                .start(this::moveTaskLoop);
    }

    /**
     * 提交任务执行，不返回承载线程。
     * <p>
     * 先尝试交给核心执行器；被拒（核心队列已满）时转入缓冲队列，由搬运线程接管；
     * 核心队列与缓冲队列皆满、或执行器已关闭时，在提交线程方以
     * {@link MicroTaskExecutor.FinallyType#NOT_ACCEPT} 收尾，不抛异常。
     *
     * @param task           获取到工作线程后执行的任务，不能为null
     * @param finallyHandler 任务提交的收尾回调，不能为null
     * @throws NullPointerException task 或 finallyHandler 为null
     */
    @Override
    public void submit(Runnable task, FinallyHandler finallyHandler) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(finallyHandler, "finallyHandler must not be null");
        Runnable wrapped = withExecuteFinally(task, finallyHandler);
        try {
            threadPoolExecutor.execute(wrapped);
        } catch (RejectedExecutionException e) {
            if (threadPoolExecutor.isShutdown()) {
                notAccepted(finallyHandler);
                return;
            }
            log.debug("core queue is full, submit to task cache");
            if (!cacheQueue.offer(wrapped)) {
                notAccepted(finallyHandler);
            }
        }
    }

    /**
     * 包装任务：任务执行完毕（正常返回或抛出异常）后，在承载执行的工作线程方以
     * {@link MicroTaskExecutor.FinallyType#EXECUTED} 收尾。
     *
     * @param task           待执行的任务
     * @param finallyHandler 收尾回调
     * @return 带执行完毕收尾通知的任务
     */
    private static Runnable withExecuteFinally(Runnable task, FinallyHandler finallyHandler) {
        return () -> {
            try {
                task.run();
            } finally {
                finallyHandler.onFinally(new MicroTaskExecutor.FinallySignal(
                        MicroTaskExecutor.FinallySignal.CallbackThread.WORKER,
                        MicroTaskExecutor.FinallyType.EXECUTED));
            }
        };
    }

    /**
     * 以未受理收尾：任务未被执行器接受，在提交线程方回调，调用方据此降级。
     *
     * @param finallyHandler 收尾回调
     */
    private static void notAccepted(FinallyHandler finallyHandler) {
        finallyHandler.onFinally(new MicroTaskExecutor.FinallySignal(
                MicroTaskExecutor.FinallySignal.CallbackThread.SUBMITTER,
                MicroTaskExecutor.FinallyType.NOT_ACCEPT));
    }

    /**
     * 搬运线程主循环：阻塞等待缓冲任务，取到即搬进核心执行器，直到执行器关闭被唤醒退出。
     */
    private void moveTaskLoop() {
        while (!threadPoolExecutor.isShutdown()) {
            Runnable task;
            try {
                task = cacheQueue.take();
            } catch (InterruptedException e) {
                // 关闭时由 close 中断唤醒
                Thread.currentThread().interrupt();
                break;
            }
            submitWithBackoff(task);
        }
    }

    /**
     * 带指数退避地把任务提交进核心执行器：核心队列仍满时退避重试，执行器关闭时任务退回缓冲队列。
     *
     * @param task 待搬运的任务
     */
    private void submitWithBackoff(Runnable task) {
        long backoffNanos = INIT_BACKOFF_NANOS;
        while (!threadPoolExecutor.isShutdown()) {
            try {
                threadPoolExecutor.execute(task);
                return;
            } catch (RejectedExecutionException e) {
                LockSupport.parkNanos(backoffNanos);
                backoffNanos = Math.min(backoffNanos << 1, MAX_BACKOFF_NANOS);
            }
        }
        // 执行器已关闭，任务退回缓冲队列，交由 close 兜底消费
        if (!cacheQueue.offer(task)) {
            // 缓冲队列此刻也可能是满的（关闭竞态下的真实形状：主线程还没来得及掏空它），
            // offer 静默失败就等于把已受理的任务丢掉。就地跑掉它，异常照 consumeCacheTasks
            // 的方式记账，不让一个坏任务打断搬运线程收尾。
            try {
                task.run();
            } catch (Exception e) {
                log.error("run cached task on close failed", e);
            }
        }
    }

    /**
     * 关闭执行器：不再受理新提交，已受理任务继续执行。
     * <p>
     * 先关闭核心执行器，再中断搬运线程并短暂等待其退出，最后消费缓冲队列中剩余的任务
     * （这些任务已被 {@link #submit(Runnable, FinallyHandler)} 受理，不能丢失）。重复调用为幂等操作。
     */
    @Override
    public void close() {
        threadPoolExecutor.shutdown();
        taskMoveThread.interrupt();
        try {
            taskMoveThread.join(MOVER_JOIN_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        consumeCacheTasks();
    }

    /**
     * 消费缓冲队列中的剩余任务：由调用线程就地执行，保证已受理的任务在关闭时不被丢弃。
     */
    private void consumeCacheTasks() {
        Runnable task;
        while ((task = cacheQueue.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                log.error("consume cached task error", e);
            }
        }
    }
}
