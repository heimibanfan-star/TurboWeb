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
 * 弹性线程池，实现 {@link ThreadPool}。
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
 * 本类以<b>实例</b>为使用单位：队列、执行器与搬运线程均归属各实例，不共享全局任务状态，
 * 构造即完成初始化，参数由调用方经构造器指定，同一JVM内可并存多个互不干扰的池。
 *
 * <pre>{@code
 * ThreadPool pool = new ElasticThreadPool(
 *         1024,   // 缓冲队列容量
 *         2,      // 核心队列容量
 *         10      // 最大线程数
 * );
 * boolean accepted = pool.execute(() -> doDiskIo());
 * pool.shutdown();
 * }</pre>
 */
public class ElasticThreadPool implements ThreadPool {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ElasticThreadPool.class);

    /** 线程池组编号，用于区分同一JVM内创建的多个实例 */
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
            Thread thread = new Thread(r, "ElasticThreadPool-" + groupId + "-" + threadId.getAndIncrement());
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
     * 构造弹性线程池。
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
    public ElasticThreadPool(int cacheQueue, int coreQueue, int maxThreadNum) {
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
                .name("ElasticThreadPool-Mover-" + groupId)
                .start(this::moveTaskLoop);
    }

    /**
     * 提交任务。
     * <p>
     * 先尝试交给核心执行器；被拒（核心队列已满）时转入缓冲队列，由搬运线程接管；
     * 缓冲队列也满时返回 {@code false}，任务被丢弃。
     *
     * @param task 任务，不能为null
     * @return 任务是否被接受
     * @throws NullPointerException       任务为null
     * @throws RejectedExecutionException 线程池已关闭
     */
    @Override
    public boolean execute(Runnable task) {
        Objects.requireNonNull(task, "task can not be null");
        if (threadPoolExecutor.isShutdown()) {
            throw new RejectedExecutionException("ElasticThreadPool has been shut down");
        }
        try {
            threadPoolExecutor.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            if (threadPoolExecutor.isShutdown()) {
                throw e;
            }
            log.debug("core queue is full, submit to task cache");
            return cacheQueue.offer(task);
        }
    }

    /**
     * 搬运线程主循环：阻塞等待缓冲任务，取到即搬进核心执行器，直到线程池关闭被唤醒退出。
     */
    private void moveTaskLoop() {
        while (!threadPoolExecutor.isShutdown()) {
            Runnable task;
            try {
                task = cacheQueue.take();
            } catch (InterruptedException e) {
                // 关闭时由 shutdown 中断唤醒
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
        // 执行器已关闭，任务退回缓冲队列，交由 shutdown 兜底消费
        if (!cacheQueue.offer(task)) {
            // 缓冲队列此刻也可能是满的（关闭竞态下的真实形状：主线程还没来得及掏空它），
            // offer 静默失败就等于把"execute 已返回 true 的任务"丢掉，违反 ThreadPool
            // 的接受契约。就地跑掉它，异常照 consumeCacheTasks 的方式记账，
            // 不让一个坏任务打断搬运线程收尾。
            try {
                task.run();
            } catch (Exception e) {
                log.error("run cached task on close failed", e);
            }
        }
    }

    /**
     * 关闭线程池：不再接受新任务，已提交任务继续执行。
     * <p>
     * 先关闭核心执行器，再中断搬运线程并短暂等待其退出，最后消费缓冲队列中剩余的任务
     * （这些任务已被 {@link #execute(Runnable)} 接受，不能丢失）。重复调用为幂等操作。
     */
    @Override
    public void shutdown() {
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
     * 消费缓冲队列中的剩余任务：由调用线程就地执行，保证已接受的任务在关闭时不被丢弃。
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

    @Override
    public boolean isShutdown() {
        return threadPoolExecutor.isShutdown();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return threadPoolExecutor.awaitTermination(timeout, unit);
    }
}
