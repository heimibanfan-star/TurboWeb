package top.turboweb.client;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * turboweb的http客户端抽象接口
 */
public interface TurboHttpClient <T> {

    class Config {
        final HttpHeaders headers = new DefaultHttpHeaders();
        final Map<String, String> urlArgs = new HashMap<>();

        /**
         * 添加请求头
         * @param consumer 添加请求头
         * @return this
         */
        public Config headers(Consumer<HttpHeaders> consumer) {
            consumer.accept(headers);
            return this;
        }

        /**
         * 添加url参数
         * @param consumer 添加url参数
         * @return this
         */
        public Config urlArgs(Consumer<Map<String, String>> consumer) {
            consumer.accept(urlArgs);
            return this;
        }
    }

    /**
     * 发起http请求
     * @param path 请求路径
     * @param method 请求方法
     * @param consumer 配置
     * @return 转换器返回结果
     */
    T request(String path, HttpMethod method, Consumer<Config> consumer);

    T request(String path, HttpMethod method);

    T request(String path);

    T get(String path, Consumer<Config> consumer);

    T get(String path);

    T post(String path, Consumer<Config> consumer);

    T post(String path, Object data, Consumer<Config> consumer);

    T put(String path);

    T put(String path, Consumer<Config> consumer);

    T put(String path, Object data, Consumer<Config> consumer);

    T delete(String path);

    T delete(String path, Consumer<Config> consumer);
}
