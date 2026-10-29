package top.turboweb.http.server.constant;

/**
 * 服务器装配期登记进 {@link top.turboweb.server.runtime.ResourceManager} 的资源名常量：
 * 登记侧与使用方按名共用本类，避免散落的字符串字面量。
 */
public final class ResourceNames {

    /**
     * 磁盘IO线程池：承载文件流写出等磁盘读取任务的调度
     */
    public static final String DISK_IO_THREAD_POOL = "diskIoThreadPool";

    /**
     * 请求微任务执行器：承载请求任务的调度与限流
     */
    public static final String MICRO_TASK_EXECUTOR = "microTaskExecutor";

    private ResourceNames() {
    }
}
