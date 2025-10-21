package top.turboweb.http.processor.convertor;

import io.netty.handler.codec.http.HttpResponse;

import java.nio.charset.Charset;

/**
 * HTTP 响应转换器接口。
 * <p>
 * 用于将业务方法或中间件的返回结果转换为 {@link HttpResponse} 对象，
 * 以便在 TurboWeb 的内核处理器链中直接返回给客户端。
 * </p>
 */
public interface HttpResponseConverter {


    /**
     * 将任意返回值转换为 HTTP 响应。
     *
     * @param result 业务逻辑或中间件返回的对象
     * @return 转换后的 {@link HttpResponse} 对象，可直接发送给客户端
     */
    HttpResponse convertor(Object result);

}
