package top.turboweb.client;

import io.netty.handler.codec.http.*;
import org.apache.hc.core5.net.URIBuilder;
import top.turboweb.client.converter.Converter;
import top.turboweb.client.engine.HttpClientEngine;
import top.turboweb.commons.exception.TurboHttpClientException;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 默认的http客户端
 */
public class DefaultTurboHttpClient <T> implements TurboHttpClient <T> {

    private final Converter<T> converter;
    private final HttpClientEngine httpClientEngine;


    public DefaultTurboHttpClient(HttpClientEngine engine, Converter<T> converter) {
        this.httpClientEngine = engine;
        this.converter = converter;
    }

    @Override
    public T request(String path, HttpMethod method, Consumer<Config> consumer) {
        Config config = new Config();
        consumer.accept(config);
        HttpRequest request = buildRequest(path, method, null, config);
        // 发送请求
        HttpResponse response = httpClientEngine.send(request);
        return converter.convert(response);
    }

    @Override
    public T request(String path, HttpMethod method) {
        return request(path, HttpMethod.GET, config -> {});
    }

    @Override
    public T request(String path) {
        return request(path, HttpMethod.GET);
    }

    @Override
    public T get(String path, Consumer<Config> consumer) {
        return request(path, HttpMethod.GET, consumer);
    }

    @Override
    public T get(String path) {
        return request(path);
    }

    @Override
    public T post(String path, Consumer<Config> consumer) {
        return null;
    }

    @Override
    public T post(String path, Object data, Consumer<Config> consumer) {
        return null;
    }

    @Override
    public T put(String path) {
        return null;
    }

    @Override
    public T put(String path, Consumer<Config> consumer) {
        return null;
    }

    @Override
    public T put(String path, Object data, Consumer<Config> consumer) {
        return null;
    }

    @Override
    public T delete(String path) {
        return request(path, HttpMethod.DELETE);
    }

    @Override
    public T delete(String path, Consumer<Config> consumer) {
        return request(path, HttpMethod.DELETE, consumer);
    }

    /**
     * 构建请求
     * @param path 请求路径
     * @param method 请求方式
     * @param data 请求数据
     * @param config 请求配置
     * @return HttpRequest
     */
    private HttpRequest buildRequest(String path, HttpMethod method, Object data, Config config) {
        if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
            // 判断是否需要构造url参数
            if (!config.urlArgs.isEmpty()) {
                try {
                    URIBuilder builder = new URIBuilder(path);
                    // 拼接参数
                    config.urlArgs.forEach(builder::addParameter);
                    path = builder.build().toString();
                } catch (URISyntaxException e) {
                    throw new TurboHttpClientException("Invalid url:" + path);
                }
            }
            return new DefaultHttpRequest(HttpVersion.HTTP_1_1, method, path, config.headers);
        } else {
            throw new UnsupportedOperationException("unSupport");
        }
    }
}
