package top.turboweb.client.result;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.client.converter.Converter;

import java.util.Map;

/**
 * 客户端结果
 */
public class ClientResult {

    private final HttpResponse response;
    private final Converter converter;

    public ClientResult(HttpResponse response, Converter converter) {
        this.response = response;
        this.converter = converter;
    }

    /**
     * 获取数据
     * @param type 数据类型
     * @param <T> 数据类型
     * @return 数据
     */
    public <T> T as(Class<T> type) {
        return converter.convert(response, type);
    }

    /**
     * 获取数据
     * @param object 数据对象
     * @param <T> 数据类型
     * @return 数据
     */
    public <T> T as(T object) {
        return converter.convert(response, object);
    }

    /**
     * 获取数据
     *
     * @return 数据
     */
    public Map<?, ?> as() {
        return converter.convert(response, Map.class);
    }

    /**
     * 获取响应头
     * @return 响应头
     */
    public HttpHeaders headers() {
        return response.headers();
    }

    /**
     * 获取状态码
     * @return 状态码
     */
    public int status() {
        return response.status().code();
    }
}
