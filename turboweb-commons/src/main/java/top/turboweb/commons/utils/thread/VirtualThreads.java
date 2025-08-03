package top.turboweb.commons.utils.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 虚拟线程工具类
 */
public final class VirtualThreads {

    private static final ExecutorService POOL;

    static {
        POOL = Executors.newVirtualThreadPerTaskExecutor();
    }

    private VirtualThreads() {
    }

    /**
     * 创建虚拟线程并执行任务
     *
     * @param task 线程任务
     * @param name 线程名称
     */
    public static void startThread(Runnable task, String name) {
        Thread.ofVirtual().name(name).start(task);
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
