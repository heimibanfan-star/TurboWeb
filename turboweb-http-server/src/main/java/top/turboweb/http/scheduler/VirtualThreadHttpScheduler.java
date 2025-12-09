package top.turboweb.http.scheduler;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.*;
import top.turboweb.commons.constants.FontColors;
import top.turboweb.commons.utils.thread.VirtualThreads;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.scheduler.strategy.ResponseStrategy;
import top.turboweb.http.scheduler.strategy.ResponseStrategyContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于虚拟线程（Virtual Thread）的同步阻塞型 HTTP 调度器。
 * <p>
 * 该调度器是 TurboWeb 框架的默认实现，用于在虚拟线程上执行 HTTP 请求的业务逻辑。
 * 它在保持同步代码风格的同时，仍然具备极高的并发处理能力。
 *
 * <h2>核心特性</h2>
 * <ul>
 *     <li><b>虚拟线程调度：</b>每个 HTTP 请求在独立的虚拟线程中处理，确保 I/O 线程永不阻塞。</li>
 *     <li><b>限流控制：</b>支持基于信号量（{@link Semaphore}）的最大并发限制和线程挂起等待机制。</li>
 *     <li><b>自适应拒绝：</b>在并发量超过上限或挂起线程超限时自动返回 HTTP 429（Too Many Requests）。</li>
 *     <li><b>响应策略：</b>通过 {@link ResponseStrategyContext} 选择合适的响应策略并写入响应。</li>
 *     <li><b>性能日志：</b>可选输出请求耗时与状态信息，支持多种 HTTP 方法的彩色控制台输出。</li>
 * </ul>
 *
 * <h3>线程模型</h3>
 * <p>
 * 本调度器由 Netty I/O 线程触发执行，但会立即在虚拟线程中运行实际业务逻辑。
 * 因此，开发者可以像编写同步代码一样编写控制逻辑，而不会阻塞 Netty 的 I/O 事件循环。
 *
 * <h3>限流策略</h3>
 * <p>
 * 当 {@code enableLimit=true} 时，调度器启用以下机制：
 * <ul>
 *     <li>通过 {@link Semaphore} 控制最大并发线程数（{@code limit}）。</li>
 *     <li>若无可用许可，则进入挂起队列（最多 {@code maxSuspendThreadNum} 个）。</li>
 *     <li>挂起线程在 {@code timeout} 毫秒内仍未获取许可，则返回 429 响应。</li>
 * </ul>
 *
 * <p><b>线程安全性：</b>本类实例通常为单例线程安全实现，可在多连接共享。</p>
 */
public class VirtualThreadHttpScheduler implements HttpScheduler {

    /** 请求处理的核心执行链（核心链或业务处理链）。 */
    protected final Processor processorChain;
    /** HTTP 方法与控制台输出颜色映射表。 */
    private final Map<String, String> colors;
    /** 响应策略上下文，用于选择合适的响应写出方式。 */
    private final ResponseStrategyContext responseStrategyContext;
    /** 是否启用限流机制。 */
    private final boolean enableLimit;
    /** 当前处于挂起等待的线程数量。 */
    private final AtomicInteger suspendThreads = new AtomicInteger(0);
    /** 最大可挂起等待的线程数量。 */
    private final int maxSuspendThreadNum;
    /** 请求超时时间（毫秒）。 */
    private final long timeout;
    /** 并发许可控制器，用于限制同时执行的虚拟线程数。 */
    private final Semaphore permission;
    /** 是否打印请求日志。 */
    protected boolean showRequestLog = true;
    /** 虚拟线程命名前缀。 */
    private static final String THREAD_NAME = "turboweb-http-handler";

    {
        colors = Map.of(
                "GET", FontColors.GREEN,
                "POST", FontColors.YELLOW,
                "PUT", FontColors.BLUE,
                "DELETE", FontColors.RED,
                "PATCH", FontColors.MAGENTA
        );
    }

    /**
     * 创建一个默认（无限流）的虚拟线程调度器。
     *
     * @param processorChain 请求处理链
     */
    public VirtualThreadHttpScheduler(Processor processorChain) {
        this(processorChain, false, 0, 0, 0);
    }

    /**
     * 创建带限流策略的虚拟线程调度器。
     *
     * @param processorChain       请求处理链
     * @param enableLimit          是否启用限流
     * @param limit                最大并发线程数（即同时持有的信号量许可数）
     * @param maxSuspendThreadNum  最大允许挂起等待的线程数
     * @param timeout              挂起线程等待许可的超时时间（毫秒）
     */
    public VirtualThreadHttpScheduler(Processor processorChain, boolean enableLimit, int limit, int maxSuspendThreadNum, long timeout) {
        this.processorChain = processorChain;
        this.enableLimit = enableLimit;
        this.maxSuspendThreadNum = maxSuspendThreadNum;
        this.timeout = timeout;
        permission = new Semaphore(limit);
        responseStrategyContext = new ResponseStrategyContext(enableLimit);
    }

    /**
     * 执行 HTTP 请求。
     * <p>
     * 请求首先由 I/O 线程触发，然后在独立的虚拟线程中执行，
     * 保证业务逻辑阻塞不会影响 I/O 事件循环。
     * <p>
     * 若开启限流，则根据许可数和挂起队列控制请求是否立即执行、等待或拒绝。
     *
     * @param request 完整的 HTTP 请求对象
     * @param session 当前连接的会话上下文
     */
    @Override
    public void execute(FullHttpRequest request, ConnectSession session) {
        long startTime = System.nanoTime();
        if (!enableLimit) {
            VirtualThreads.startThread(() -> doExecute(request, session), THREAD_NAME);
            return;
        }
        boolean prePermission;
        // 判断是否许可耗尽，并且超过最大挂起数
        if (!(prePermission = permission.tryAcquire()) && suspendThreads.get() >= maxSuspendThreadNum) {
            writeResponse(session, request, toManyRequestResponse(), startTime);
            return;
        }
        // 创建虚拟线程
        VirtualThreads.startThread(() -> {
            boolean hasPermission = prePermission;
            long waitTime = timeout;
            for (; ; ) {
                if (hasPermission) {
                    // 如果拥有凭据直接执行
                    try {
                        doExecute(request, session, startTime);
                        return;
                    } finally {
                        // 释放凭据
                        permission.release();
                    }
                } else if (permission.tryAcquire()) {
                    hasPermission = true;
                } else {
                    // 判断是否到达最大挂起数
                    if (!trySuspendedThread()) {
                        // 再次尝试获取凭据
                        if (permission.tryAcquire()) {
                            hasPermission = true;
                        } else {
                            writeResponse(session, request, toManyRequestResponse(), startTime);
                            return;
                        }
                    } else {
                        if (waitTime <= 0) {
                            writeResponse(session, request, toManyRequestResponse(), startTime);
                            return;
                        }
                        // 挂起线程
                        long time = System.currentTimeMillis();
                        try {
                            hasPermission = permission.tryAcquire(waitTime, TimeUnit.MILLISECONDS);
                            if (!hasPermission) {
                                // 减少挂起的线程数
                                activeThread();
                                // 拒绝请求
                                writeResponse(session, request, toManyRequestResponse(), startTime);
                            }
                        } catch (InterruptedException e) {
                            activeThread();
                            waitTime = waitTime - (System.currentTimeMillis() - time);
                        }
                    }
                }
            }
        }, THREAD_NAME);
    }

    /**
     * 尝试增加一个挂起等待线程计数。
     *
     * @return 若未超过 {@code maxSuspendThreadNum} 并成功增加计数则返回 {@code true}，否则返回 {@code false}
     */
    private boolean trySuspendedThread() {
        for (; ; ) {
            int n = suspendThreads.get();
            // 判断是否超过最大挂起数
            if (n >= maxSuspendThreadNum) {
                return false;
            }
            if (suspendThreads.compareAndSet(n, n + 1)) {
                return true;
            }
        }
    }

    /**
     * 减少挂起线程计数。
     * <p>用于挂起线程被唤醒或超时时恢复线程计数。</p>
     */
    private void activeThread() {
        suspendThreads.decrementAndGet();
    }

    /**
     * 执行核心请求处理逻辑。
     *
     * @param request 请求对象
     * @param session 连接会话
     */
    private void doExecute(FullHttpRequest request, ConnectSession session) {
        doExecute(request, session, System.nanoTime());
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
     *
     * @param request   请求对象
     * @param time      执行耗时（纳秒）
     * @param isSuccess 响应是否成功发送
     */
    private void log(FullHttpRequest request, long time, boolean isSuccess) {
        String method = request.method().name();
        if (!colors.containsKey(method)) {
            return;
        }
        String color = colors.get(method);
        String uri = request.uri();
        if (time > 1000000) {
            System.out.println(color + "%s  %s  耗时:%sms  state:%s".formatted(method, uri, time / 1000000, isSuccess ? "success" : "fail") + FontColors.RESET);
        } else {
            System.out.println(color + "%s  %s  耗时:%sµs  state:%s".formatted(method, uri, time / 1000, isSuccess ? "success" : "fail") + FontColors.RESET);
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
                log(request, System.nanoTime() - startTime, future.isSuccess());
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
