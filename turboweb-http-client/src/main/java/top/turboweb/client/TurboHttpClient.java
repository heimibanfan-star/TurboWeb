package top.turboweb.client;

import io.netty.handler.codec.http.*;
import top.turboweb.client.interceptor.RequestInterceptor;
import top.turboweb.client.interceptor.ResponseInterceptor;
import top.turboweb.client.result.ClientResult;
import top.turboweb.client.result.Stream;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * TurboWeb HTTP 客户端核心接口。
 * <p>
 * 提供统一的 HTTP 请求发起能力，支持 GET / POST / PUT / DELETE 等标准方法，
 * 并允许通过拦截器机制对请求与响应进行统一处理。
 * <p>
 * 拦截器模型说明：
 * <ul>
 *     <li>普通请求（request / get / post 等）支持请求拦截器与响应拦截器</li>
 *     <li><b>流式请求（requestStream）仅支持请求拦截器，不支持响应拦截器</b></li>
 * </ul>
 * <p>
 * 流式请求通常用于 SSE;文件下载、Chunked 待研究，
 * 响应数据的消费与生命周期由调用方自行控制。
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
            // 设置请求格式为表单
            headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED);
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
            // 设置请求格式为 JSON
            headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            return this;
        }
    }

    /**
     * 发起普通 HTTP 请求（非流式）。
     * <p>
     * 执行流程：
     * <ol>
     *     <li>构建请求并执行请求拦截器</li>
     *     <li>发送请求并获取完整响应</li>
     *     <li>依次执行所有响应拦截器</li>
     * </ol>
     *
     * @param path     请求路径（相对或绝对 URL）
     * @param method   HTTP 方法
     * @param data     请求体对象，可为 null
     * @param consumer 请求配置回调
     * @return ClientResult 封装完整响应结果
     */
    ClientResult request(String path, HttpMethod method, Object data, Consumer<Config> consumer);

    /**
     * 发起 HTTP 流式请求。
     * <p>
     * 特性说明：
     * <ul>
     *     <li>仅执行请求拦截器（{@link RequestInterceptor}）</li>
     *     <li>不会执行响应拦截器（{@link ResponseInterceptor}）</li>
     *     <li>响应体以流的形式返回，不保证一次性读取完整内容</li>
     * </ul>
     *
     * @param path     请求路径
     * @param method   HTTP 方法
     * @param data     请求体对象，可为 null
     * @param consumer 请求配置回调
     * @return Stream 流式响应封装
     */
    Stream requestStream(String path, HttpMethod method, Object data, Consumer<Config> consumer);

    ClientResult request(String path, HttpMethod method, Consumer<Config> consumer);

    /**
     * 发起 HTTP 请求，返回流式结果。
     *
     * @param path     请求路径
     * @param method   HTTP 方法
     * @param consumer 配置回调
     * @return Stream 流式结果封装
     */
    Stream requestStream(String path, HttpMethod method, Consumer<Config> consumer);

    ClientResult request(String path, HttpMethod method);

    /**
     * 发起 HTTP 请求，返回流式结果。
     *
     * @param path     请求路径
     * @param method   HTTP 方法
     * @return Stream 流式结果封装
     */
    Stream requestStream(String path, HttpMethod method);

    ClientResult request(String path);

    /**
     * 发起 HTTP 请求，返回流式结果。
     *
     * @param path     请求路径
     * @return Stream 流式结果封装
     */
    Stream requestStream(String path);

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

    ClientResult post(String path, Object data);

    ClientResult post(String path);

    /**
     * 发起 PUT 请求。
     */
    ClientResult put(String path);

    ClientResult put(String path, Consumer<Config> consumer);

    ClientResult put(String path, Object data, Consumer<Config> consumer);

    ClientResult put(String path, Object data);

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
