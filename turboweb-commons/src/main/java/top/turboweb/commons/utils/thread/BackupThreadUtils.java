package top.turboweb.commons.utils.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 备用线程池
 */
public class BackupThreadUtils {

    private static final Logger log = LoggerFactory.getLogger(BackupThreadUtils.class);
    private static volatile ThreadPoolExecutor EXECUTOR;
    private static final ReentrantLock LOCK = new ReentrantLock();

    private static class InternalThreadFactory implements ThreadFactory {

        private static final String NAME_PREFIX = "back-up-thread-";
        private static final AtomicInteger threadNum = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            int num = threadNum.getAndUpdate(x -> (x == Integer.MAX_VALUE) ? 0 : x + 1);
            return new Thread(r, NAME_PREFIX + num);
        }
    }

    /**
     * 提交任务
     *
     * @param runnable 任务
     */
    public static void execute(Runnable runnable) {
        if (EXECUTOR == null) {
            LOCK.lock();
            try {
                if (EXECUTOR == null) {
                    int cpuNum = Runtime.getRuntime().availableProcessors();
                    EXECUTOR = new ThreadPoolExecutor(
                            cpuNum * 2,
                            cpuNum * 8,
                            5,
                            TimeUnit.MINUTES,
                            new ArrayBlockingQueue<>(cpuNum * 2),
                            new InternalThreadFactory(),
                            new ThreadPoolExecutor.CallerRunsPolicy()
                    );
                    EXECUTOR.allowCoreThreadTimeOut(true);
                }
            } finally {
                LOCK.unlock();
            }
        }
        EXECUTOR.execute(runnable);
    }
}
