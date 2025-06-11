package top.turboweb.http.response.negotiator;

import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.FileRegionResponse;
import top.turboweb.http.response.IgnoredHttpResponse;
import top.turboweb.http.response.sync.InternalSseEmitter;
import top.turboweb.http.response.sync.SseEmitter;

/**
 * 用于同步模型调度器的响应协商器
 */
public class SyncHttpResponseNegotiator implements HttpResponseNegotiator {

    @Override
    public HttpResponse negotiate(HttpContext context, Object middlewareResult) {
        // 判断是否写入内容
        if (context.isWrite()) {
            return context.getResponse();
        }
        // 如果为空返回空内容
        switch (middlewareResult) {
            case null -> {
                context.text("");
                return context.getResponse();
            }
            // 判断返回值是否是响应对象
            case HttpResponse httpResponse -> {
                // 如果是sse发射器直接忽略
                if (httpResponse instanceof SseEmitter sseEmitter) {
                    // 初始化sse发射器
                    InternalSseEmitter internalSseEmitter = (InternalSseEmitter) sseEmitter;
                    internalSseEmitter.initSse();
                    httpResponse = IgnoredHttpResponse.ignore();
                    // 判断是否需要释放内存
                    if (context.getResponse() != httpResponse) {
                        context.getResponse().release();
                    }
                } else if (httpResponse instanceof FileRegionResponse fileRegionResponse) {
                    httpResponse = fileRegionResponse.getFileResponse(context.getConnectSession());
                }
                return httpResponse;
            }
            // 处理字符串类型
            case String s -> {
                // 写入ctx
                context.text(s);
                return context.getResponse();
            }
            default -> {
                // 其他类型作为json写入
                context.json(middlewareResult);
                return context.getResponse();
            }
        }
    }
}
