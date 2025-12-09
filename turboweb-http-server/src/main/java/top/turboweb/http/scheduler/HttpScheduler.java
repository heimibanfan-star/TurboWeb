package top.turboweb.http.scheduler;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.http.connect.ConnectSession;

/**
 * HTTP 调度器接口。
 * <p>
 * 该接口定义了 TurboWeb 框架中用于执行 HTTP 请求的统一调度抽象层，
 * 用于将 Netty I/O 线程接收到的完整 HTTP 请求交由业务线程或虚拟线程执行。
 * 不同的实现类可根据自身策略（如虚拟线程、线程池限流、反应式执行等）
 * 实现不同的调度模型，以平衡性能与吞吐量。
 * </p>
 *
 * <p>典型实现包括：
 * <ul>
 *   <li>{@link top.turboweb.http.scheduler.VirtualThreadHttpScheduler}：基于虚拟线程的同步阻塞模型</li>
 * </ul>
 * </p>
 */
public interface HttpScheduler {

    /**
     * 执行 HTTP 请求。
     * <p>
     * 调度器接收到完整的 HTTP 请求后，会将其交由内部处理链（Processor）执行。
     * 不同的实现可以选择在虚拟线程、固定线程池或反应式流中运行。
     * </p>
     *
     * @param request 完整的 {@link FullHttpRequest} 请求对象，包含头、体和方法等信息。
     * @param session 当前连接的会话对象，封装了请求通道及上下文信息。
     */
    void execute(FullHttpRequest request, ConnectSession session);

    /**
     * 设置是否打印请求日志。
     * <p>
     * 若启用，将在请求完成后输出包括 HTTP 方法、URI、耗时与状态的性能日志。
     * 默认实现类通常以彩色格式打印日志，便于区分请求类型。
     * </p>
     *
     * @param showRequestLog {@code true} 表示打印请求日志；{@code false} 表示关闭日志输出。
     */
    void setShowRequestLog(boolean showRequestLog);
}
