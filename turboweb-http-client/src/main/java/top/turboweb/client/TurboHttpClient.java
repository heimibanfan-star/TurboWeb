package top.turboweb.client;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import top.turboweb.client.interceptor.RequestInterceptor;
import top.turboweb.client.interceptor.ResponseInterceptor;
import top.turboweb.client.result.ClientResult;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * TurboWeb HTTP 客户端核心接口。
 * <p>
 * 提供统一的 HTTP 请求发起能力，支持 GET/POST/PUT/DELETE 等标准方法，
 * 并允许对请求和响应进行全局或局部拦截处理。
 * 适用于高并发、微服务调用场景下的客户端封装。
 */
public interface TurboHttpClient {

    /**
     * HTTP 请求参数键值对封装。
     */
    record Entry(String key, String value) {
    }

    /**
     * HTTP 请求参数容器。
     * <p>
     * 用于收集 URL 查询参数（query string）或表单参数（form data）。
     * 提供链式添加能力，支持多参数组合。
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
     * HTTP 请求配置。
     * <p>
     * 支持设置请求头、URL 参数、表单参数以及请求体数据，
     * 可通过 {@link Consumer} 回调进行灵活配置。
     */
    class Config {
        final HttpHeaders headers = new DefaultHttpHeaders();
        final Params queryArgs = new Params();
        final Params formArgs = new Params();
        Object data = null;

        /**
         * 设置请求头。
         *
         * @param consumer HttpHeaders 配置回调
         * @return 当前 Config 实例，用于链式调用
         */
        public Config headers(Consumer<HttpHeaders> consumer) {
            consumer.accept(headers);
            return this;
        }

        /**
         * 设置 URL 查询参数。
         *
         * @param consumer Params 配置回调
         * @return 当前 Config 实例，用于链式调用
         */
        public Config query(Consumer<Params> consumer) {
            consumer.accept(queryArgs);
            return this;
        }

        /**
         * 设置表单参数。
         *
         * @param consumer Params 配置回调
         * @return 当前 Config 实例，用于链式调用
         */
        public Config form(Consumer<Params> consumer) {
            consumer.accept(formArgs);
            return this;
        }

        /**
         * 设置请求体数据。
         * <p>
         * 对于 POST/PUT 等方法，可传入任意对象（通常为 JSON 可序列化对象）。
         *
         * @param data 请求体对象
         * @return 当前 Config 实例，用于链式调用
         */
        public Config data(Object data) {
            this.data = data;
            return this;
        }
    }

    /**
     * 发起 HTTP 请求。
     *
     * @param path     请求路径（相对或绝对 URL）
     * @param method   HTTP 方法
     * @param data     请求体对象，可为 null
     * @param consumer 请求配置回调
     * @return ClientResult 封装响应结果及状态信息
     */
    ClientResult request(String path, HttpMethod method, Object data, Consumer<Config> consumer);

    ClientResult request(String path, HttpMethod method, Consumer<Config> consumer);

    ClientResult request(String path, HttpMethod method);

    ClientResult request(String path);

    /**
     * 发起 GET 请求。
     *
     * @param path     请求路径
     * @param consumer 请求配置回调，可设置 headers/query
     * @return ClientResult 响应封装
     */
    ClientResult get(String path, Consumer<Config> consumer);

    ClientResult get(String path);

    /**
     * 发起 POST 请求。
     *
     * @param path     请求路径
     * @param consumer 请求配置回调
     * @return ClientResult 响应封装
     */
    ClientResult post(String path, Consumer<Config> consumer);

    ClientResult post(String path, Object data, Consumer<Config> consumer);

    /**
     * 发起 PUT 请求。
     */
    ClientResult put(String path);

    ClientResult put(String path, Consumer<Config> consumer);

    ClientResult put(String path, Object data, Consumer<Config> consumer);

    /**
     * 发起 DELETE 请求。
     */
    ClientResult delete(String path);

    ClientResult delete(String path, Consumer<Config> consumer);

    /**
     * 注册请求拦截器。
     * <p>
     * 拦截器在请求发起前执行，可用于添加公共 headers、鉴权签名、日志记录等。
     *
     * @param interceptor 请求拦截器实现
     * @return 当前 TurboHttpClient 实例，支持链式调用
     */
    TurboHttpClient addRequestInterceptor(RequestInterceptor interceptor);

    /**
     * 注册响应拦截器。
     * <p>
     * 拦截器在请求返回后执行，可用于统一处理响应、异常封装、日志记录等。
     *
     * @param interceptor 响应拦截器实现
     * @return 当前 TurboHttpClient 实例，支持链式调用
     */
    TurboHttpClient addResponseInterceptor(ResponseInterceptor interceptor);
}
