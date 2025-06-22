package top.turboweb.http.processor;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * TurboWeb的内核处理器
 */
public abstract class Processor {

    private final Processor nextProcessor;

    public Processor(Processor nextProcessor) {
        this.nextProcessor = nextProcessor;
    }

    /**
     * 中间件处理方法
     * @param fullHttpRequest 请求对象
     */
    public abstract HttpResponse invoke(FullHttpRequest fullHttpRequest);

    /**
     * 调用下一个内核中间件
     *
     * @param fullHttpRequest 完整的http请求对象
     * @return http响应结果
     */
    protected HttpResponse next(FullHttpRequest fullHttpRequest) {
        if (nextProcessor != null) {
            return nextProcessor.invoke(fullHttpRequest);
        }
        return null;
    }
}
