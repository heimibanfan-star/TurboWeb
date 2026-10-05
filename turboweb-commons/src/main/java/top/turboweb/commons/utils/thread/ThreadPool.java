package top.turboweb.commons.utils.thread;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 线程池抽象。
 * <p>
 * 统一 TurboWeb 内部各类专用线程池（磁盘操作、弹性扩容等）的对外契约：提交任务并返回是否被接受、
 * 关闭与终止等待。与 {@link java.util.concurrent.ExecutorService} 不同，本接口只保留框架实际需要
 * 的能力，不暴露 {@code submit}/{@code invokeAll} 一族。
 * <p>
 * {@link #execute(Runnable)} 的布尔返回值表达背压：{@code false} 表示核心队列与缓冲队列均已满、
 * 任务未被接受，调用方据此降级（例如放弃本次下载并记录日志），而不是阻塞等待。
 * <p>
 * 关闭语义：{@link #shutdown()} 不再接受新任务，已提交的任务继续执行；重复调用须为幂等。
 */
public interface ThreadPool extends AutoCloseable {

    /**
     * 提交任务。
     *
     * @param task 任务，不能为null
     * @return 任务是否被线程池接受；{@code false} 表示队列已满、任务被丢弃
     * @throws NullPointerException         任务为null
     * @throws RejectedExecutionException   线程池已关闭
     */
    boolean execute(Runnable task);

    /**
     * 关闭线程池：不再接受新任务，已提交的任务继续执行。重复调用为幂等操作。
     */
    void shutdown();

    /**
     * 线程池是否已关闭。
     *
     * @return {@code true} 表示已调用过 {@link #shutdown()}
     */
    boolean isShutdown();

    /**
     * 等待线程池终止。
     *
     * @param timeout 最长等待时间
     * @param unit    时间单位
     * @return {@code true} 表示线程池在超时前已终止
     * @throws InterruptedException 等待期间线程被中断
     */
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 关闭线程池，等价于 {@link #shutdown()}，便于配合 try-with-resources 使用。
     */
    @Override
    default void close() {
        shutdown();
    }
}