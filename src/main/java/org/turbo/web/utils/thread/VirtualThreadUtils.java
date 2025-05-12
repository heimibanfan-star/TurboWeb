package org.turbo.web.utils.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 虚拟线程工具类
 */
public class VirtualThreadUtils {

    private static final ExecutorService POOL;

    static {
        POOL = Executors.newVirtualThreadPerTaskExecutor();
    }

    private VirtualThreadUtils() {
    }

    /**
     * 执行异步任务
     *
     * @param task 任务
     */
    public static void execute(Runnable task) {
        POOL.execute(task);
    }

    /**
     * 获取线程池
     *
     * @return 线程池
     */
    public static ExecutorService getPool() {
        return POOL;
    }

}
