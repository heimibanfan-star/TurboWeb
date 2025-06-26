package top.turboweb.http.scheduler.impl;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.response.IgnoredHttpResponse;
import top.turboweb.http.response.InternalSseEmitter;
import top.turboweb.http.response.SseEmitter;

/**
 * 同步模型的HTTP调度器
 */
public abstract class SyncHttpScheduler extends AbstractHttpScheduler{


    public SyncHttpScheduler(
            Processor processorChain,
            Class<?> subClass
    ) {
        super(processorChain, subClass);
    }

    /**
     * 异步运行任务
     * @param runnable 任务
     */
    protected abstract void runTask(Runnable runnable);

    @Override
    public void execute(FullHttpRequest request, ConnectSession session) {
        this.runTask(() -> {
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
}
