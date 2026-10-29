package top.turboweb.concurrent.thread;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 弹性微任务执行器，实现 {@link MicroTaskExecutor}：以固定上限的平台线程承载虚拟线程无法卸载的
 * IO阻塞型任务（如磁盘读写），避免此类任务长期占住虚拟线程的载体线程。
 * <p>
 * 由核心执行器 {@link ThreadPoolExecutor} 与一个二级缓冲队列 {@code cacheQueue} 组成，
 * 另有一条虚拟搬运线程负责把缓冲队列中的任务搬运进执行器，并在退出时创建虚拟收尾线程
 * 执行完毕缓冲队列中的全部剩余任务：
 * <ul>
 *   <li>核心线程数固定为1，核心队列（{@link ArrayBlockingQueue}）积压时按最大线程数扩容，
 *   空闲线程按存活时间回收（核心线程同样允许超时回收），即"弹性"所在；</li>
 *   <li>核心队列满时任务转入缓冲队列，由搬运线程接管，从而在突发流量下平滑削峰而不丢任务；</li>
 *   <li>搬运时执行器若仍拒绝（核心队列依旧满），按指数退避重试，避免空转。</li>
 * </ul>
 * <p>
 * 收尾语义：任务受理后执行完毕（正常返回或抛出异常）在承载执行的工作线程方以
 * {@link MicroTaskExecutor.FinallyType#EXECUTED} 收尾；核心队列与缓冲队列皆满、或缓冲队列
 * 已被搬运线程置为关闭时，在提交线程方以 {@link MicroTaskExecutor.FinallyType#NOT_ACCEPT}
 * 收尾，不抛异常；受理阶段抛出的非拒绝异常先以未受理收尾，原异常继续向提交方传播。
 * <p>
 * 关闭语义：close 后到搬运线程退出前是宽限期，此间被核心执行器拒绝的提交照常进入缓冲队列，
 * 由收尾线程保证执行完毕；搬运线程退出时将缓冲队列置为关闭，此后提交一律以未受理收尾。
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

    /** 关闭时等待搬运线程退出的最长时间 */
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

    /** 任务缓冲队列元素：任务与其收尾回调成对排队，无论搬运还是收尾方执行都能正确收尾 */
    private record CacheEntry(Runnable task, FinallyHandler finallyHandler) {
    }

    /** 任务缓冲队列：核心队列满后的二级缓冲 */
    private final BlockingQueue<CacheEntry> cacheQueue;

    /** 核心执行器 */
    private final ThreadPoolExecutor threadPoolExecutor;

    /** 搬运线程（虚拟线程），负责把缓冲队列中的任务搬进核心执行器 */
    private final Thread taskMoveThread;

    /**
     * 关闭门禁：搬运线程与提交方的关闭检查、入队之间的互斥用 {@link ReentrantLock} 达成，
     * 不用监视器锁——临界区内含缓冲队列入队，虚拟线程等队列内部锁时不必被监视器钉在载体线程上
     */
    private final ReentrantLock closeLock = new ReentrantLock();

    /** 关闭状态：搬运线程退出前置位，置位后缓冲队列不再受理新任务；仅由 closeLock 保护读写 */
    private volatile boolean closed;

    /** 缓冲队列收尾线程，由搬运线程退出时创建；close 等待其执行完毕 */
    private volatile Thread cacheFinalizerThread;

    /** 执行器组编号，用于线程命名 */
    private final int groupId;

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
        this.groupId = GROUP_ID.getAndIncrement();
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
     * 核心队列与缓冲队列皆满、或缓冲队列已被置为关闭时，在提交线程方以
     * {@link MicroTaskExecutor.FinallyType#NOT_ACCEPT} 收尾，不抛异常。
     * 受理阶段抛出的非拒绝异常先以未受理收尾，原异常继续向提交方传播。
     * 缓冲队列关闭状态的检查与入队在同一把重入锁下互斥，保证任务要么在置位前入队并被执行，
     * 要么在置位后被拒，不会入队后无人接管。
     *
     * @param task           获取到工作线程后执行的任务，不能为null
     * @param finallyHandler 任务提交的收尾回调，不能为null
     * @throws NullPointerException task 或 finallyHandler 为null
     */
    @Override
    public void submit(Runnable task, FinallyHandler finallyHandler) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(finallyHandler, "finallyHandler must not be null");
        try {
            threadPoolExecutor.execute(withExecuteFinally(task, finallyHandler));
        } catch (RejectedExecutionException e) {
            closeLock.lock();
            try {
                if (closed) {
                    notAccepted(finallyHandler);
                    return;
                }
                // 关闭宽限期内核心执行器拒绝的提交照常入缓冲队列，由搬运或收尾方执行完毕
                log.debug("core queue is full, submit to task cache");
                if (!cacheQueue.offer(new CacheEntry(task, finallyHandler))) {
                    notAccepted(finallyHandler);
                }
            } finally {
                closeLock.unlock();
            }
        } catch (Throwable e) {
            // 受理阶段的非拒绝异常：先以未受理收尾，原异常继续向提交方传播
            notAccepted(finallyHandler);
            throw e;
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
     * <p>
     * 退出路径固定两步：先在锁内置位缓冲队列的关闭状态（此后提交方一律被拒，
     * 不再有任何任务进入缓冲队列），再创建虚拟收尾线程执行完毕队列中的全部剩余任务。
     */
    private void moveTaskLoop() {
        try {
            while (!threadPoolExecutor.isShutdown()) {
                CacheEntry entry;
                try {
                    entry = cacheQueue.take();
                } catch (InterruptedException e) {
                    // 关闭时由 close 中断唤醒
                    Thread.currentThread().interrupt();
                    break;
                }
                try {
                    submitWithBackoff(entry);
                } catch (Throwable e) {
                    // 单个任务的搬运失败只记账，不拖垮搬运线程对后续任务的处理
                    log.error("move cached task failed", e);
                }
            }
        } finally {
            closeLock.lock();
            try {
                // 与提交方的关闭检查、入队互斥：置位前的成功入队任务全部先于收尾线程启动
                closed = true;
            } finally {
                closeLock.unlock();
            }
            cacheFinalizerThread = Thread.ofVirtual()
                    .name("ElasticMicroTaskExecutor-Finalizer-" + groupId)
                    .start(this::consumeCacheTasks);
        }
    }

    /**
     * 带指数退避地把任务提交进核心执行器：核心队列仍满时退避重试；
     * 执行器关闭后就地执行完毕任务（已受理的任务不退回缓冲队列，避免与收尾线程的
     * 排空产生交接竞态）；受理阶段抛出的非拒绝异常以未受理收尾后记账放行。
     *
     * @param entry 待搬运的任务及其收尾回调
     */
    private void submitWithBackoff(CacheEntry entry) {
        Runnable wrapped = withExecuteFinally(entry.task(), entry.finallyHandler());
        long backoffNanos = INIT_BACKOFF_NANOS;
        while (!threadPoolExecutor.isShutdown()) {
            try {
                threadPoolExecutor.execute(wrapped);
                return;
            } catch (RejectedExecutionException e) {
                LockSupport.parkNanos(backoffNanos);
                backoffNanos = Math.min(backoffNanos << 1, MAX_BACKOFF_NANOS);
            } catch (Throwable e) {
                notAccepted(entry.finallyHandler());
                log.error("accept cached task failed", e);
                return;
            }
        }
        // 执行器已关闭：任务已被受理，就地执行完毕
        try {
            wrapped.run();
        } catch (Throwable e) {
            log.error("run cached task on close failed", e);
        }
    }

    /**
     * 关闭执行器：已受理任务继续执行完毕，缓冲队列被搬运线程置为关闭后不再受理新提交。
     * <p>
     * 先关闭核心执行器，再中断搬运线程并短暂等待其退出；搬运线程退出时创建的收尾线程
     * 会执行完毕缓冲队列中的全部剩余任务（这些任务已被 {@link #submit(Runnable, FinallyHandler)}
     * 受理，不能丢失），本方法等待收尾线程执行完毕后返回；搬运线程未能在等待期内退出时，
     * 收尾线程在其退出后异步完成排空，任务不丢失。重复调用为幂等操作。
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
        Thread finalizer = cacheFinalizerThread;
        if (finalizer != null) {
            try {
                finalizer.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 消费缓冲队列中的剩余任务：由收尾线程执行，逐任务兜底异常，
     * 保证已受理的任务在关闭时不被丢弃、单个坏任务不中断其余任务的排空。
     */
    private void consumeCacheTasks() {
        CacheEntry entry;
        while ((entry = cacheQueue.poll()) != null) {
            try {
                withExecuteFinally(entry.task(), entry.finallyHandler()).run();
            } catch (Throwable e) {
                log.error("consume cached task error", e);
            }
        }
    }
}
