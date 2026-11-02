package top.turboweb.http.server;

/**
 * 服务器装配期登记进 {@link top.turboweb.server.runtime.ResourceManager} 的资源名常量：
 * 登记侧与使用方按名共用本类，避免散落的字符串字面量。
 */
public final class ResourceNames {

    /**
     * 请求微任务执行器：承载请求任务的调度与限流
     */
    public static final String MICRO_TASK_EXECUTOR = "microTaskExecutor";

    /**
     * 任务卸载线程池：承载流式响应写出凭据的完成任务，使流式续流脱离写出落定线程
     */
    public static final String TASK_OFFLOAD_THREAD_POOL = "taskOffloadThreadPool";

    private ResourceNames() {
    }
}
