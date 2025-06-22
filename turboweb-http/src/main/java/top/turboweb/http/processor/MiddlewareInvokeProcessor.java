package top.turboweb.http.processor;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.middleware.Middleware;

/**
 * 执行中间件的处理器
 */
public class MiddlewareInvokeProcessor extends Processor{

    private final Middleware chain;

    public MiddlewareInvokeProcessor(Middleware chain) {
        super(null);
        this.chain = chain;
    }

    @Override
    public HttpResponse invoke(FullHttpRequest fullHttpRequest) {
        return null;
    }
}
