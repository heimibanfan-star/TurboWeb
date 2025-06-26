package top.turboweb.http.processor;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connect.ConnectSession;

/**
 * TurboWeb的内核处理器
 */
public abstract class Processor {

    private Processor nextProcessor;

    /**
     * 中间件处理方法
     *
     * @param fullHttpRequest 请求对象
     * @param connectSession 连接会话
     */
    public abstract HttpResponse invoke(FullHttpRequest fullHttpRequest, ConnectSession connectSession);

    /**
     * 调用下一个内核处理器
     *
     * @param fullHttpRequest 完整的http请求对象
     * @param connectSession 连接会话
     * @return http响应结果
     */
    protected HttpResponse next(FullHttpRequest fullHttpRequest, ConnectSession connectSession) {
        if (nextProcessor != null) {
            return nextProcessor.invoke(fullHttpRequest, connectSession);
        }
        return null;
    }

    /**
     * 设置下一个内核处理器
     *
     * @param nextProcessor 下一个内核中间件
     */
    public void setNextProcessor(Processor nextProcessor) {
        this.nextProcessor = nextProcessor;
    }
}
