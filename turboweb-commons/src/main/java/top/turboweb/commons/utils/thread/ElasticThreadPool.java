package top.turboweb.commons.utils.thread;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 弹性线程池
 */
public class ElasticThreadPool implements ExecutorService {

    private static final AtomicInteger GROUP_ID = new AtomicInteger(0);

    private static class ElasticThreadFactory implements ThreadFactory {
        private final AtomicLong threadId = new AtomicLong(0);
        private final int groupId;

        public ElasticThreadFactory(int groupId) {
            this.groupId = groupId;
        }

        @Override
        public Thread newThread(Runnable r) {
            String threadName = "ElasticThreadPool-" + groupId + "-" + threadId.getAndIncrement();
            Thread thread = new Thread(r, threadName);
            thread.setDaemon(true);
            return thread;
        }
    }

    // 任务缓冲队列
    private final BlockingQueue<Runnable> cacheQueue;

    private final ThreadPoolExecutor threadPoolExecutor;

    public ElasticThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, int cacheTaskNumn) {
        threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        cacheQueue = new LinkedBlockingQueue<>(cacheTaskNumn);
        startTaskMoveThread();
    }

    public ElasticThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, int cacheTaskNumn) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new ElasticThreadFactory(GROUP_ID.getAndIncrement()), cacheTaskNumn);
    }

    private void startTaskMoveThread() {
        Thread.ofVirtual().start(() -> {
           for (;;) {
               try {
                   Runnable task = cacheQueue.poll(5, TimeUnit.SECONDS);
                   if (task == null) continue;
                   else if (isShutdown()) break;
                   // 尝试提交任务
                   long avoidNanos = 1000;
                   for (;;) {
                       try {
                           threadPoolExecutor.execute(task);
                           break;
                       } catch (RejectedExecutionException e) {
                           if (threadPoolExecutor.isShutdown()) {
                               boolean ignore = cacheQueue.offer(task);
                               break;
                           }
                           LockSupport.parkNanos(avoidNanos);
                           if (avoidNanos < 1000_1000_00) {
                               avoidNanos *= 10;
                           }
                       }
                   }
               } catch (Exception ignore) {
               }
           }
        });
    }

    @Override
    public void shutdown() {
        threadPoolExecutor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return threadPoolExecutor.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        boolean shutdown = threadPoolExecutor.isShutdown();
        // 判断剩余的任务
        if (!cacheQueue.isEmpty()) {
            if (cacheQueue.size() < 8) {
                syncConsumerTask();
            } else {
                asyncConsumerTask();
            }
        }
        return shutdown;
    }

    /**
     * 同步消费缓存任务
     */
    private void syncConsumerTask() {
        Runnable task;
        while ((task = cacheQueue.poll()) != null) {
            try {
                task.run();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * 异步消费缓存任务
     */
    private void asyncConsumerTask() {
        int n = cacheQueue.size() / 8;
        if (n > 0) {
            int cpuNum = Runtime.getRuntime().availableProcessors();
            int threadNum = Math.min(n, cpuNum);
            for (int i = 0; i < threadNum; i++) {
                new Thread(() -> {
                    while (!cacheQueue.isEmpty()) {
                        try {
                            Runnable task = cacheQueue.poll();
                            if (task == null) {
                                break;
                            }
                            task.run();
                        } catch (Exception ignore) {
                        }
                    }
                }, this.hashCode() + "-" + i).start();
            }
        } else {
            syncConsumerTask();
        }
    }

    @Override
    public boolean isTerminated() {
        return threadPoolExecutor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return threadPoolExecutor.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return threadPoolExecutor.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return threadPoolExecutor.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return threadPoolExecutor.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return threadPoolExecutor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return threadPoolExecutor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return threadPoolExecutor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return threadPoolExecutor.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        try {
            threadPoolExecutor.execute(command);
        } catch (RejectedExecutionException e) {
            if (threadPoolExecutor.isShutdown()) {
                throw e;
            }
            boolean offered = cacheQueue.offer(command);
            if (!offered) {
                throw new RejectedExecutionException("cache queue is full");
            }
        }
    }
}
