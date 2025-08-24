package top.turboweb.client.result;

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
    public <T> T data(Class<T> type) {
        return converter.convert(response, type);
    }

    /**
     * 获取数据
     *
     * @return 数据
     */
    public Map<?, ?> data() {
        return converter.convert(response, Map.class);
    }
}
