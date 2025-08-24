package top.turboweb.client.converter;

import io.netty.handler.codec.http.HttpResponse;

import java.util.Map;

/**
 * 响应转换器
 */
@FunctionalInterface
public interface Converter {

    /**
     * 转换响应
     * @param response 响应
     * @param type 响应类型
     * @param <T> 类型泛型
     * @return 转换后的响应
     */
    <T> T convert(HttpResponse response, Class<T> type);
}
