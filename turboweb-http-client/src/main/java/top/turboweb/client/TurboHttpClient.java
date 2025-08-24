package top.turboweb.client;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.hc.core5.http.ContentType;
import top.turboweb.client.result.ClientResult;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * turboweb的http客户端抽象接口
 */
public interface TurboHttpClient {

    /**
     * 参数实体对象
     */
    record Entry(String key, String value) {
    }

    /**
     * 参数容器
     */
    class Params {
        final List<Entry> entries = new LinkedList<>();

        /**
         * 添加url参数
         * @param key 参数名
         * @param value 参数值
         * @return this
         */
        public Params add(String key, String value) {
            entries.add(new Entry(key, value));
            return this;
        }
    }

    /**
     * 请求配置
     */
    class Config {
        final HttpHeaders headers = new DefaultHttpHeaders();
        final Params queryArgs = new Params();
        final Params formArgs = new Params();
        Object data = null;

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
        public Config query(Consumer<Params> consumer) {
            consumer.accept(queryArgs);
            return this;
        }

        /**
         * 添加表单参数
         * @param consumer 添加表单参数
         * @return this
         */
        public Config form(Consumer<Params> consumer) {
            consumer.accept(formArgs);
            return this;
        }

        /**
         * 设置请求数据
         * @param data 请求数据
         * @return this
         */
        public Config data(Object data) {
            this.data = data;
            return this;
        }
    }

    /**
     * 发起http请求
     * @param path 请求路径
     * @param method 请求方法
     * @param data 请求数据
     * @param consumer 配置
     * @return 转换器返回结果
     */
    ClientResult request(String path, HttpMethod method, Object data, Consumer<Config> consumer);

    ClientResult request(String path, HttpMethod method, Consumer<Config> consumer);

    ClientResult request(String path, HttpMethod method);

    ClientResult request(String path);

    ClientResult get(String path, Consumer<Config> consumer);

    ClientResult get(String path);

    ClientResult post(String path, Consumer<Config> consumer);

    ClientResult post(String path, Object data, Consumer<Config> consumer);

    ClientResult put(String path);

    ClientResult put(String path, Consumer<Config> consumer);

    ClientResult put(String path, Object data, Consumer<Config> consumer);

    ClientResult delete(String path);

    ClientResult delete(String path, Consumer<Config> consumer);
}
