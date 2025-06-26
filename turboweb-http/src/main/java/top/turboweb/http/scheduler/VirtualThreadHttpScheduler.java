package top.turboweb.http.scheduler;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.commons.constants.FontColors;
import top.turboweb.commons.utils.thread.VirtualThreadUtils;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.response.IgnoredHttpResponse;
import top.turboweb.http.response.InternalSseEmitter;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.handler.DefaultHttpResponseHandler;
import top.turboweb.http.response.handler.HttpResponseHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 使用虚拟县城的阻塞线程调度器
 */
public class VirtualThreadHttpScheduler implements HttpScheduler {

    protected final Processor processorChain;
    private final Map<String, String> colors = new ConcurrentHashMap<>(4);
    private final HttpResponseHandler httpResponseHandler = new DefaultHttpResponseHandler();
    protected boolean showRequestLog = true;

    {
        colors.put("GET", FontColors.GREEN);
        colors.put("POST", FontColors.YELLOW);
        colors.put("PUT", FontColors.BLUE);
        colors.put("DELETE", FontColors.RED);
        colors.put("PATCH", FontColors.MAGENTA);
    }

    public VirtualThreadHttpScheduler(
            Processor processorChain
    ) {
        this.processorChain = processorChain;
    }

    @Override
    public void execute(FullHttpRequest request, ConnectSession session) {
        VirtualThreadUtils.execute(() -> {
            long startTime = System.nanoTime();
            try {
                HttpResponse response = doExecute(request, session);
                writeResponse(session, request, response, startTime);
            } finally {
                request.release();
            }
        });
    }

    private HttpResponse doExecute(FullHttpRequest request, ConnectSession session) {
        HttpResponse httpResponse = processorChain.invoke(request, session);
        // 判断是否是SSE发射器
        if (httpResponse instanceof SseEmitter sseEmitter) {
            InternalSseEmitter internalSseEmitter = (InternalSseEmitter) sseEmitter;
            internalSseEmitter.initSse();
            httpResponse = IgnoredHttpResponse.ignore();
        }
        return httpResponse;
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
        ChannelFuture channelFuture = httpResponseHandler.writeHttpResponse(response, session);
        // 打印性能日志
        if (showRequestLog && channelFuture != null) {
            channelFuture.addListener(future -> {
                log(request, System.nanoTime() - startTime, future.isSuccess());
            });
        }
    }
}
