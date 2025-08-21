package top.turboweb.client;

import io.netty.handler.codec.http.HttpMethod;
import top.turboweb.client.converter.Converter;

import java.util.function.Consumer;

/**
 * turboweb的http客户端抽象接口
 */
public interface TurboHttpClient <T> {

    class Config {

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
