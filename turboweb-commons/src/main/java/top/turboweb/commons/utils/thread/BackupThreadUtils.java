package top.turboweb.commons.utils.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 备用线程池
 */
public class BackupThreadUtils {

    private static final Logger log = LoggerFactory.getLogger(BackupThreadUtils.class);
    private static volatile ThreadPoolExecutor EXECUTOR;
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static volatile BlockingQueue<Runnable> TASKS;
    private static volatile boolean isInit = false;

    private static class InternalThreadFactory implements ThreadFactory {

        private static final String NAME_PREFIX = "back-up-thread-";
        private static final AtomicInteger threadNum = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            int num = threadNum.getAndUpdate(x -> (x == Integer.MAX_VALUE) ? 0 : x + 1);
            return new Thread(r, NAME_PREFIX + num);
        }
    }

    public static void init(int cacheQueue, int coreQueue, int maxThreadNum) {
        if (isInit) {
            return;
        }
        LOCK.lock();
        try {
            if (isInit) {
                return;
            }
            // 创建缓冲队列
            TASKS = new LinkedBlockingQueue<>(cacheQueue);
            // 创建核心线程池
            EXECUTOR = new ThreadPoolExecutor(
                    1,
                    maxThreadNum,
                    5,
                    TimeUnit.MINUTES,
                    new ArrayBlockingQueue<>(coreQueue),
                    new InternalThreadFactory(),
                    new ThreadPoolExecutor.AbortPolicy()
            );
            EXECUTOR.allowCoreThreadTimeOut(true);
            // 启动哨兵线程
            startVirtualSentinel();
            // 修改标识位
            isInit = true;
        } finally {
            LOCK.unlock();
        }
    }
    /**
     * 启动虚拟哨兵线程
     */
    private static void startVirtualSentinel() {
        Thread.ofVirtual().start(() -> {
           while (true) {
               try {
                   Runnable task = TASKS.take();
                   long sleepTime = 1000;
                   while (true) {
                       try {
                           submitTask(task);
                       } catch (RejectedExecutionException e) {
                           if (sleepTime < 16384000) {
                               sleepTime <<= 1;
                           }
                           LockSupport.parkNanos(sleepTime);
                           continue;
                       }
                       break;
                   }
               } catch (Exception ignore) {
               }
           }
        });
    }

    /**
     * 提交任务
     *
     * @param runnable 任务
     */
    public static boolean execute(Runnable runnable) {
        if (!isInit) {
            throw new IllegalStateException("BackUpThreadUtils has not been initialized yet");
        }
        // 尝试直接执行任务
        try {
            EXECUTOR.execute(runnable);
            return true;
        } catch (RejectedExecutionException e) {
            log.debug("core queue is full, submit to task cache");
            // 尝试将任务加入缓冲区
            return TASKS.offer(runnable);
        }
    }

    /**
     * 提交任务
     *
     * @param task 任务
     */
    private static void submitTask(Runnable task) {
        EXECUTOR.execute(task);
    }
}
