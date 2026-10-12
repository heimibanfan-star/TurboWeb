package top.turboweb.commons.utils.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 工作窃取线程的工具
 */
public class WorkStealThreadUtils {

    private static volatile ExecutorService executorService;
    private static final ReentrantLock LOCK = new ReentrantLock();

    private WorkStealThreadUtils() {}

    public static ExecutorService getExecutorService() {
        if (executorService != null) {
            return executorService;
        }
        LOCK.lock();
        try {
            if (executorService != null) {
                return executorService;
            }
            executorService = Executors.newWorkStealingPool();
            return executorService;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * 提交任务
     * @param task 任务
     */
    public static void execute(Runnable task) {
        getExecutorService().execute(task);
    }
}
