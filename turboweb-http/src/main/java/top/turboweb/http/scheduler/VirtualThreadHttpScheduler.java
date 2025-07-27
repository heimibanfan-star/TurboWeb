package top.turboweb.http.scheduler;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.*;
import top.turboweb.commons.constants.FontColors;
import top.turboweb.commons.utils.thread.VirtualThreadUtils;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.scheduler.strategy.ResponseStrategy;
import top.turboweb.http.scheduler.strategy.ResponseStrategyContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 使用虚拟县城的阻塞线程调度器
 */
public class VirtualThreadHttpScheduler implements HttpScheduler {

    protected final Processor processorChain;
    private final Map<String, String> colors = new ConcurrentHashMap<>(4);
    private final ResponseStrategyContext responseStrategyContext = new ResponseStrategyContext();
    private final boolean enableLimit;
    private final int limit;
    private final BlockingQueue<Thread> waitThreads;
    private final int cacheThreadNum;
    private final long timeout;
    private final AtomicInteger threadNum = new AtomicInteger(0);
    protected boolean showRequestLog = true;

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

    public VirtualThreadHttpScheduler(Processor processorChain, boolean enableLimit, int limit, int cacheThreadNum, long timeout) {
        this.processorChain = processorChain;
        this.enableLimit = enableLimit;
        this.limit = limit;
        waitThreads = cacheThreadNum > 0 ? new LinkedBlockingQueue<>(cacheThreadNum) : null;
        this.cacheThreadNum = cacheThreadNum;
        this.timeout = timeout;
    }

    @Override
    public void execute(FullHttpRequest request, ConnectSession session) {
        long startTime = System.nanoTime();
        if (!enableLimit) {
            VirtualThreadUtils.execute(() -> doExecute(request, session));
            return;
        }
        boolean preAcquire;
        // 如果凭据不足，并且缓冲队列已满直接拒绝
        if (!(preAcquire = tryAcquireCredential()) && waitThreads.size() >= cacheThreadNum) {
            writeResponse(session, request, toManyRequestResponse(), startTime);
            return;
        }
        // 创建虚拟线程执行
        VirtualThreadUtils.execute(() -> {
            boolean acquire = preAcquire;
            long waitNanos = TimeUnit.MILLISECONDS.toNanos(timeout);
            for (;;) {
                if (acquire) {
                    try {
                        doExecute(request, session);
                        return;
                    } finally {
                        // 释放凭据
                        int c = releaseCredential();
                        for (int n = 0; n < c; n++) {
                            Thread t;
                            if ((t = waitThreads.poll()) == null) {
                                break;
                            }
                            LockSupport.unpark(t);
                        }
                    }
                } else if (tryAcquireCredential()) {
                    acquire = true;
                } else {
                    // 尝试加入阻塞队列
                    if (!waitThreads.offer(Thread.currentThread())) {
                        if (tryAcquireCredential()) {
                            acquire = true;
                        } else {
                            // 拒绝请求
                            writeResponse(session, request, toManyRequestResponse(), startTime);
                            return;
                        }
                    } else {
                        // 再次尝试获取凭据
                        if (tryAcquireCredential()) {
                            acquire = true;
                            // 从阻塞队列中删除自己
                            boolean removed = waitThreads.remove(Thread.currentThread());
                            // 将唤醒的机会交给别的线程
                            if (!removed) {
                                Thread t = waitThreads.poll();
                                if (t != null) {
                                    LockSupport.unpark(t);
                                }
                            }
                        } else {
                            long startNanos = System.nanoTime();
                            // 打断自己
                            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(waitNanos));
                            waitNanos = Math.abs(waitNanos - (System.nanoTime() - startNanos));
                            // 判断是否被唤醒
                            if (waitNanos <= 0) {
                                boolean removed = waitThreads.remove(Thread.currentThread());
                                // 如果被别的线程提前订阅，将唤醒机会交给别的线程
                                if (!removed) {
                                    Thread t = waitThreads.poll();
                                    if (t != null) {
                                        LockSupport.unpark(t);
                                    }
                                }
                                writeResponse(session, request, toManyRequestResponse(), startTime);
                                return;
                            }
                            // 处理虚假唤醒的情况
                            boolean ignore = waitThreads.remove(Thread.currentThread());
                        }
                    }
                }
            }
        });
    }

    /**
     * 尝试获取凭据
     *
     * @return 是否成功获取
     */
    private boolean tryAcquireCredential() {
        int count = threadNum.getAndIncrement();
        if (count < limit) {
            return true;
        }
        // 归还凭据
        threadNum.getAndDecrement();
        return false;
    }

    /**
     * 释放凭据
     */
    private int releaseCredential() {
        return limit - threadNum.decrementAndGet();
    }

    /**
     * 执行
     *
     * @param request  请求对象
     * @param session  session对象
     */
    private void doExecute(FullHttpRequest request, ConnectSession session) {
        long startTime = System.nanoTime();
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
