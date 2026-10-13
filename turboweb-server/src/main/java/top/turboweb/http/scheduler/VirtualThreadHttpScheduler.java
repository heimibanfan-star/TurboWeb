package top.turboweb.http.scheduler;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.concurrent.thread.LimitedVirtualThreadResource;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.concurrent.thread.UnlimitedVirtualThreadResource;
import top.turboweb.concurrent.thread.VirtualThreadResource;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.scheduler.strategy.ResponseStrategy;
import top.turboweb.http.scheduler.strategy.ResponseStrategyContext;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 基于虚拟线程（Virtual Thread）的同步阻塞型 HTTP 调度器。
 * <p>
 * 该调度器是 TurboWeb 框架的默认实现，用于在虚拟线程上执行 HTTP 请求的业务逻辑。
 * 它在保持同步代码风格的同时，仍然具备极高的并发处理能力。
 *
 * <h2>核心特性</h2>
 * <ul>
 *     <li><b>虚拟线程调度：</b>每个 HTTP 请求在独立的虚拟线程中处理，确保 I/O 线程永不阻塞。</li>
 *     <li><b>限流控制：</b>并发执行量统一交由虚拟线程资源管理，启用限流时资源耗尽按最大挂起数与最大挂起时间排队等待。</li>
 *     <li><b>自适应拒绝：</b>任务获取资源失败（挂起额度已满或等待超时）时自动返回 HTTP 429（Too Many Requests）。</li>
 *     <li><b>响应策略：</b>通过 {@link ResponseStrategyContext} 选择合适的响应策略并写入响应。</li>
 *     <li><b>性能日志：</b>开关开启时经日志器输出每个请求的方法、URI、耗时与响应发送状态。</li>
 * </ul>
 *
 * <h3>线程模型</h3>
 * <p>
 * 本调度器由 Netty I/O 线程触发执行，但会立即在虚拟线程中运行实际业务逻辑。
 * 因此，开发者可以像编写同步代码一样编写控制逻辑，而不会阻塞 Netty 的 I/O 事件循环。
 *
 * <h3>限流策略</h3>
 * <p>
 * 本调度器不自行管理并发许可与挂起队列，统一委托虚拟线程资源（{@link VirtualThreadResource}）：
 * {@code enableLimit=true} 时构造 {@link LimitedVirtualThreadResource}（非公平抢占），
 * 以 {@code limit} 为最大并发执行数、{@code maxSuspendThreadNum} 为最大挂起数、
 * {@code timeout} 为单个任务排队等待资源的超时时间（毫秒），
 * 挂起额度已满或等待预算耗尽的请求触发拒绝回调，返回 429 响应；
 * {@code enableLimit=false} 时构造 {@link UnlimitedVirtualThreadResource}，
 * 每个请求直接进入新建虚拟线程执行，永不拒绝。
 * </p>
 *
 * <p><b>线程安全性：</b>本类实例通常为单例线程安全实现，可在多连接共享。</p>
 */
public class VirtualThreadHttpScheduler implements HttpScheduler {

    /** 请求处理的核心执行链（核心链或业务处理链）。 */
    protected final Processor processorChain;
    /** 响应策略上下文，用于选择合适的响应写出方式。 */
    private final ResponseStrategyContext responseStrategyContext;
    /** 虚拟线程资源，承载请求任务的线程创建与限流：启用限流时为受限实现，否则为非受限实现。 */
    private final VirtualThreadResource virtualThreadResource;
    /** 是否打印请求日志。 */
    protected boolean showRequestLog = true;
    /** 虚拟线程命名前缀。 */
    private static final String THREAD_NAME = "turboweb-http-handler";
    /** 请求日志记录器，走 netty 标准日志通道。 */
    private static final InternalLogger log = InternalLoggerFactory.getInstance(VirtualThreadHttpScheduler.class);

    /**
     * 创建一个默认（无限流）的虚拟线程调度器。
     *
     * @param processorChain 请求处理链
     * @param threadPool     磁盘IO线程池，注入文件流响应策略
     */
    public VirtualThreadHttpScheduler(Processor processorChain, ThreadPool threadPool) {
        this(processorChain, threadPool, false, 0, 0, 0);
    }

    /**
     * 创建带限流策略的虚拟线程调度器。
     *
     * @param processorChain       请求处理链
     * @param threadPool           磁盘IO线程池，向下注入 {@link ResponseStrategyContext}
     *                             供文件流响应策略调度分块读取；调度器仅使用不负责其关闭
     * @param enableLimit          是否启用限流：启用时以 {@link LimitedVirtualThreadResource}（非公平抢占）
     *                             管理并发执行量，不启用时以 {@link UnlimitedVirtualThreadResource} 直通执行
     * @param limit                最大并发执行数（资源数），启用限流时不能为 0 或负数
     * @param maxSuspendThreadNum  最大允许挂起等待的线程数，启用限流时不能为负数
     * @param timeout              挂起线程等待资源的超时时间（毫秒），启用限流时不能为负数
     * @throws NullPointerException 线程池为null
     * @throws IllegalArgumentException 启用限流且上述任一参数不合规，由受限虚拟线程资源在构造期拒绝；
     *                                  不启用限流时三个限流参数不参与校验
     */
    public VirtualThreadHttpScheduler(Processor processorChain, ThreadPool threadPool, boolean enableLimit, int limit, int maxSuspendThreadNum, long timeout) {
        this.processorChain = processorChain;
        this.virtualThreadResource = enableLimit
                ? new LimitedVirtualThreadResource(limit, maxSuspendThreadNum, timeout, THREAD_NAME, false)
                : new UnlimitedVirtualThreadResource(THREAD_NAME);
        responseStrategyContext = new ResponseStrategyContext(enableLimit,
                Objects.requireNonNull(threadPool, "磁盘IO线程池不能为null"));
    }

    /**
     * 执行 HTTP 请求。
     * <p>
     * 请求首先由 I/O 线程触发，提交给虚拟线程资源后在独立的虚拟线程中执行，
     * 保证业务逻辑阻塞不会影响 I/O 事件循环。
     * <p>
     * 启用限流时，获取资源失败的任务经拒绝回调写出 429（Too Many Requests）响应：
     * 挂起额度已满在当前线程回调，排队等待超时或等待被中断在承载等待的虚拟线程回调；
     * 未启用限流时任务永不拒绝，直接执行。
     *
     * @param request 完整的 HTTP 请求对象
     * @param session 当前连接的会话上下文
     */
    @Override
    public void execute(FullHttpRequest request, ConnectSession session) {
        long startTime = System.nanoTime();
        virtualThreadResource.run(
                () -> doExecute(request, session, startTime),
                rejectionThread -> writeResponse(session, request, toManyRequestResponse(), startTime));
    }

    /**
     * 在虚拟线程中执行处理链，并写出响应。
     *
     * @param request   请求对象
     * @param session   当前连接会话
     * @param startTime 请求开始时间，用于性能统计
     */
    private void doExecute(FullHttpRequest request, ConnectSession session, long startTime) {
        try {
            HttpResponse response = processorChain.invoke(request, session);
            writeResponse(session, request, response, startTime);
        } finally {
            request.release();
        }
    }

    @Override
    public void setShowRequestLog(boolean showRequestLog) {
        this.showRequestLog = showRequestLog;
    }

    /**
     * 输出请求日志。
     * <p>
     * 经 netty 标准日志通道以 INFO 级记录，内容只用英文与 ASCII 字符，避免控制台编码乱码：
     * 请求方法、URI、耗时（超过 1 毫秒按毫秒计，否则按微秒计）与响应发送状态。
     *
     * @param request   请求对象
     * @param time      执行耗时（纳秒）
     * @param isSuccess 响应是否成功发送
     */
    private void logRequest(FullHttpRequest request, long time, boolean isSuccess) {
        String method = request.method().name();
        String uri = request.uri();
        if (time > 1000000) {
            log.info("{} {} cost:{}ms state:{}", method, uri, time / 1000000, isSuccess ? "success" : "fail");
        } else {
            log.info("{} {} cost:{}us state:{}", method, uri, time / 1000, isSuccess ? "success" : "fail");
        }
    }

    /**
     * 写出响应。
     * <p>
     * 通过 {@link ResponseStrategyContext} 动态选择合适的响应策略，
     * 并在发送完成后可选打印性能日志。
     *
     * @param session   当前连接会话
     * @param request   请求对象
     * @param response  响应对象
     * @param startTime 请求起始时间
     */
    protected void writeResponse(ConnectSession session, FullHttpRequest request, HttpResponse response, long startTime) {
        // 获取响应策略
        ResponseStrategy responseStrategy = responseStrategyContext.chooseStrategy(response);
        // 执行对应的策略
        ChannelFuture channelFuture = responseStrategy.handle(response, (InternalConnectSession) session);
        // 打印性能日志
        if (showRequestLog && channelFuture != null) {
            channelFuture.addListener(future -> {
                logRequest(request, System.nanoTime() - startTime, future.isSuccess());
            });
        }
    }

    /**
     * 构建 HTTP 429（Too Many Requests）响应。
     *
     * @return 响应对象，内容为 "too many request"
     */
    private HttpResponse toManyRequestResponse() {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TOO_MANY_REQUESTS);
        response.headers().add("Content-Type", "text/plain;charset=utf-8");
        String msg = "too many request";
        response.content().writeCharSequence(msg, StandardCharsets.UTF_8);
        response.headers().add("Content-Length", msg.length());
        return response;
    }
}
