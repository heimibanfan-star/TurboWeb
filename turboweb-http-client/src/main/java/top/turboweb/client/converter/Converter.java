package top.turboweb.client.converter;

import io.netty.handler.codec.http.HttpResponse;

/**
 * 响应转换器
 */
@FunctionalInterface
public interface Converter <T> {

    /**
     * 转换响应
     * @param response 响应
     * @return 转换后的响应
     */
    T convert(HttpResponse response);
}
