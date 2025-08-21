package top.turboweb.http.processor.convertor;

import io.netty.handler.codec.http.HttpResponse;

import java.nio.charset.Charset;

/**
 * 返回值转换器
 */
public interface HttpResponseConverter {

    /**
     * 将结果转化为为http响应
     * @param result 返回值
     * @return 转换后的返回值
     */
    HttpResponse convertor(Object result);

}
