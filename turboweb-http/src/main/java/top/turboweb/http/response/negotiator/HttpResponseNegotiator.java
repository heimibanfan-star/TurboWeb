package top.turboweb.http.response.negotiator;

import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.context.HttpContext;

/**
 * http响应协商器
 */
public interface HttpResponseNegotiator {


    /**
     * 协商
     * @param context 上下文
     * @param middlewareResult 中间件返回的结果
     * @return 协商后的响应
     */
    HttpResponse negotiate(HttpContext context, Object middlewareResult);
}
