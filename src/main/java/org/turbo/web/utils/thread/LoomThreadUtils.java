package org.turbo.web.utils.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 虚拟线程工具类
 */
public class LoomThreadUtils {

    private static final ExecutorService POOL;

    static {
        POOL = Executors.newVirtualThreadPerTaskExecutor();
    }

    private LoomThreadUtils() {
    }

    /**
     * 执行异步任务
     *
     * @param task 任务
     */
    public static void execute(Runnable task) {
        POOL.execute(task);
    }

}
