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
    private static final BlockingQueue<Runnable> TASKS = new LinkedBlockingQueue<>(4096);
    private static final ReentrantLock TASK_LOCK = new ReentrantLock();
    private static final Condition TASK_CONDITION = TASK_LOCK.newCondition();

    private static class InternalThreadFactory implements ThreadFactory {

        private static final String NAME_PREFIX = "back-up-thread-";
        private static final AtomicInteger threadNum = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            int num = threadNum.getAndUpdate(x -> (x == Integer.MAX_VALUE) ? 0 : x + 1);
            return new Thread(r, NAME_PREFIX + num);
        }
    }

    static {
        startVirtualSentinel();
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
        return TASKS.offer(runnable);
    }

    /**
     * 提交任务
     *
     * @param task 任务
     */
    private static void submitTask(Runnable task) {
        if (EXECUTOR == null) {
            LOCK.lock();
            try {
                if (EXECUTOR == null) {
                    int cpuNum = Runtime.getRuntime().availableProcessors();
                    EXECUTOR = new ThreadPoolExecutor(
                            1,
                            cpuNum * 8,
                            5,
                            TimeUnit.MINUTES,
                            new ArrayBlockingQueue<>(6),
                            new InternalThreadFactory(),
                            new ThreadPoolExecutor.AbortPolicy()
                    );
                    EXECUTOR.allowCoreThreadTimeOut(true);
                }
            } finally {
                LOCK.unlock();
            }
        }
        EXECUTOR.execute(task);
    }
}
