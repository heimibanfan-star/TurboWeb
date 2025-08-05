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
 * 使用虚拟县城的阻塞线程调度器
 */
public class VirtualThreadHttpScheduler implements HttpScheduler {

    protected final Processor processorChain;
    private final Map<String, String> colors = new ConcurrentHashMap<>(4);
    private final ResponseStrategyContext responseStrategyContext;
    private final boolean enableLimit;
    private final AtomicInteger suspendThreads = new AtomicInteger(0);
    private final int maxSuspendThreadNum;
    private final long timeout;
    private final Semaphore permission;
    protected boolean showRequestLog = true;
    private static final String THREAD_NAME = "turboweb-http-handler";

    {
        colors.put("GET", FontColors.GREEN);
        colors.put("POST", FontColors.YELLOW);
        colors.put("PUT", FontColors.BLUE);
        colors.put("DELETE", FontColors.RED);
        colors.put("PATCH", FontColors.MAGENTA);
    }

    public VirtualThreadHttpScheduler(Processor processorChain) {
        this(processorChain, false, 0, 0, 0);
    }

    public VirtualThreadHttpScheduler(Processor processorChain, boolean enableLimit, int limit, int maxSuspendThreadNum, long timeout) {
        this.processorChain = processorChain;
        this.enableLimit = enableLimit;
        this.maxSuspendThreadNum = maxSuspendThreadNum;
        this.timeout = timeout;
        permission = new Semaphore(limit);
        responseStrategyContext = new ResponseStrategyContext(enableLimit);
    }

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
     * 尝试获取凭据
     *
     * @return 是否成功获取
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
     * 减少挂起的线程数
     */
    private void activeThread() {
        suspendThreads.decrementAndGet();
    }

    /**
     * 执行任务
     */
    private void doExecute(FullHttpRequest request, ConnectSession session) {
        doExecute(request, session, System.nanoTime());
    }

    /**
     * 执行
     *
     * @param request 请求对象
     * @param session session对象
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
     * 打印日志
     *
     * @param request 请求对象
     * @param time    执行耗时
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
     * 写响应
     *
     * @param session   session对象
     * @param request   请求对象
     * @param response  响应对象
     * @param startTime 开始时间
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
     * 响应策略：请求过多
     *
     * @return 响应对象
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
