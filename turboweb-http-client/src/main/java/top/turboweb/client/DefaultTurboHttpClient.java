package top.turboweb.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.net.URIBuilder;
import top.turboweb.client.converter.Converter;
import top.turboweb.client.converter.JsonConverter;
import top.turboweb.client.engine.HttpClientEngine;
import top.turboweb.client.interceptor.RequestInterceptor;
import top.turboweb.client.interceptor.ResponseInterceptor;
import top.turboweb.client.result.ClientResult;
import top.turboweb.commons.exception.TurboHttpClientException;

import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * DefaultTurboHttpClient 是 TurboWeb HTTP 客户端的默认实现类。
 * <p>
 * 提供对 HTTP 请求的完整封装，支持 GET/POST/PUT/DELETE 方法，
 * 自动处理 URL 参数、表单参数和请求体数据。
 * <p>
 * 特性：
 * <ul>
 *     <li>支持请求/响应拦截器</li>
 *     <li>可自定义请求数据转换器（Converter）</li>
 *     <li>基于 HttpClientEngine 发起网络请求</li>
 *     <li>异常安全，拦截器返回 null 会触发异常</li>
 * </ul>
 */
public class DefaultTurboHttpClient implements TurboHttpClient {

    private final Converter converter;
    private final HttpClientEngine httpClientEngine;
    // 请求拦截器
    private final List<RequestInterceptor> requestInterceptors = new ArrayList<>();
    // 响应拦截器
    private final List<ResponseInterceptor> responseInterceptors = new ArrayList<>();

    /**
     * 构造方法，使用自定义 HttpClientEngine 与 Converter。
     *
     * @param engine    HTTP 客户端引擎，负责实际网络请求
     * @param converter 数据转换器，用于请求/响应对象与字节流的互转
     */
    public DefaultTurboHttpClient(HttpClientEngine engine, Converter converter) {
        this.httpClientEngine = engine;
        this.converter = converter;
    }

    /**
     * 构造方法，使用自定义 HttpClientEngine，默认使用 JSON 转换器。
     *
     * @param engine HTTP 客户端引擎
     */
    public DefaultTurboHttpClient(HttpClientEngine engine) {
        this(engine, new JsonConverter());
    }

    /**
     * 构造方法，使用基础 URL 初始化 HttpClientEngine，默认 JSON 转换器。
     *
     * @param baseUrl 基础 URL，用于请求路径拼接
     */
    public DefaultTurboHttpClient(String baseUrl) {
        this(new HttpClientEngine(baseUrl));
    }

    /**
     * 默认构造方法，基础 URL 为空，使用默认 JSON 转换器。
     */
    public DefaultTurboHttpClient() {
        this(new HttpClientEngine(""));
    }

    /**
     * 发起 HTTP 请求。
     *
     * @param path     请求路径
     * @param method   HTTP 方法
     * @param data     请求体数据，可为 null
     * @param consumer 请求配置回调，可设置 headers、query、form、data
     * @return ClientResult 封装响应结果
     * @throws TurboHttpClientException 当拦截器返回 null 或 URL 无效时抛出
     */
    @Override
    public ClientResult request(String path, HttpMethod method, Object data, Consumer<Config> consumer) {
        Config config = new Config();
        consumer.accept(config);
        // 对特殊的请求体设置请求类型
        if (data != null && (method == HttpMethod.POST || method == HttpMethod.PUT)) {
            config.headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        }
        HttpRequest request = buildRequest(path, method, data, config);
        // 执行所有的请求拦截器
        for (RequestInterceptor interceptor : requestInterceptors) {
            request = interceptor.intercept(request);
            if (request == null) {
                throw new TurboHttpClientException("Request interceptor return null");
            }
        }
        // 发送请求
        HttpResponse response = httpClientEngine.send(request);
        for (ResponseInterceptor interceptor : responseInterceptors) {
            response = interceptor.intercept(response);
            if (response == null) {
                throw new TurboHttpClientException("Response interceptor return null");
            }
        }
        return new ClientResult(response, converter);
    }

    @Override
    public ClientResult request(String path, HttpMethod method, Consumer<Config> consumer) {
        return request(path, method, null, consumer);
    }

    @Override
    public ClientResult request(String path, HttpMethod method) {
        return request(path, method, null, config -> {});
    }

    @Override
    public ClientResult request(String path) {
        return request(path, HttpMethod.GET, null, config -> {});
    }

    @Override
    public ClientResult get(String path, Consumer<Config> consumer) {
        return request(path, HttpMethod.GET, consumer);
    }

    @Override
    public ClientResult get(String path) {
        return request(path, HttpMethod.GET);
    }

    @Override
    public ClientResult post(String path, Consumer<Config> consumer) {
        return request(path, HttpMethod.POST, consumer);
    }

    @Override
    public ClientResult post(String path, Object data, Consumer<Config> consumer) {
        return request(path, HttpMethod.POST, data, consumer);
    }

    @Override
    public ClientResult post(String path, Object data) {
        return request(path, HttpMethod.POST, data, config -> {});
    }

    @Override
    public ClientResult post(String path) {
        return request(path, HttpMethod.POST);
    }

    @Override
    public ClientResult put(String path) {
        return request(path, HttpMethod.PUT);
    }

    @Override
    public ClientResult put(String path, Consumer<Config> consumer) {
        return request(path, HttpMethod.PUT, consumer);
    }

    @Override
    public ClientResult put(String path, Object data, Consumer<Config> consumer) {
        return request(path, HttpMethod.PUT, data, consumer);
    }

    @Override
    public ClientResult put(String path, Object data) {
        return request(path, HttpMethod.PUT, data, config -> {});
    }

    @Override
    public ClientResult delete(String path) {
        return request(path, HttpMethod.DELETE);
    }

    @Override
    public ClientResult delete(String path, Consumer<Config> consumer) {
        return request(path, HttpMethod.DELETE, consumer);
    }

    /**
     * 注册请求拦截器。
     *
     * @param interceptor 拦截器实现，非空
     * @return 当前客户端实例，支持链式调用
     * @throws TurboHttpClientException 拦截器重复添加时抛出
     */
    @Override
    public TurboHttpClient addRequestInterceptor(RequestInterceptor interceptor) {
        Objects.requireNonNull(interceptor, "interceptor can not be null");
        // 判断是否重复添加
        if (requestInterceptors.contains(interceptor)) {
            throw new TurboHttpClientException("Duplicate interceptor：" + interceptor.getClass().getName());
        }
        // 添加拦截器
        requestInterceptors.add(interceptor);
        return this;
    }

    /**
     * 注册响应拦截器。
     *
     * @param interceptor 拦截器实现，非空
     * @return 当前客户端实例，支持链式调用
     * @throws TurboHttpClientException 拦截器重复添加时抛出
     */
    @Override
    public TurboHttpClient addResponseInterceptor(ResponseInterceptor interceptor) {
        Objects.requireNonNull(interceptor, "interceptor can not be null");
        if (responseInterceptors.contains(interceptor)) {
            throw new TurboHttpClientException("Duplicate interceptor：" + interceptor.getClass().getName());
        }
        responseInterceptors.add(interceptor);
        return this;
    }

    /**
     * 构建 HttpRequest 对象，包括 URL 参数、请求体和请求头。
     *
     * @param path   请求路径
     * @param method HTTP 方法
     * @param data   请求体数据
     * @param config 请求配置
     * @return 构建完成的 HttpRequest
     * @throws TurboHttpClientException URL 无效或构建失败时抛出
     */
    private HttpRequest buildRequest(String path, HttpMethod method, Object data, Config config) {
        // 判断是否需要构造url参数
        if (!config.queryArgs.entries.isEmpty()) {
            try {
                URIBuilder builder = new URIBuilder(path);
                // 拼接参数
                config.queryArgs.entries.forEach(entry -> builder.addParameter(entry.key(), entry.value()));
                path = builder.build().toString();
            } catch (URISyntaxException e) {
                throw new TurboHttpClientException("Invalid url:" + path);
            }
        }
        // 判断请求方式是否是GET
        if (HttpMethod.GET.name().equals(method.name())) {
            return new DefaultHttpRequest(HttpVersion.HTTP_1_1, method, path, config.headers);
        }
        // 构造请求体
        ByteBuf buf = buildHttpContent(data, config);
        // 拼接完整的请求对象
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path,buf);
        // 设置请求头
        request.headers().set(config.headers);
        return request;
    }

    /**
     * 构建请求体 ByteBuf。
     *
     * @param data   请求数据
     * @param config 请求配置
     * @return 封装请求体的 ByteBuf
     * @throws TurboHttpClientException 请求体重复或 ContentType 不支持时抛出
     */
    private ByteBuf buildHttpContent(Object data, Config config) {
        // 判断请求体是否重复设置
        if (data != null && config.data != null) {
            throw new TurboHttpClientException("Request Content are repeated");
        }
        // 获取请求格式
        String contentTypeStr = config.headers.get(HttpHeaderNames.CONTENT_TYPE);
        ContentType contentType;
        if (contentTypeStr == null) {
            contentType = ContentType.APPLICATION_JSON;
        } else {
            contentType = ContentType.parse(contentTypeStr);
        }
        // 获取编码
        Charset charset = contentType.getCharset() != null ? contentType.getCharset() : StandardCharsets.UTF_8;
        // 处理请求体
        if (ContentType.APPLICATION_JSON.getMimeType().equals(contentType.getMimeType())) {
            Object requestData = data != null ? data : config.data;
            // 构造请求体
            return converter.beanConvertBuf(requestData, charset);
        } else if (ContentType.APPLICATION_FORM_URLENCODED.getMimeType().equals(contentType.getMimeType())) {
            // 获取表单参数
            Params formArgs = config.formArgs;
            // 拼接表单参数
            StringBuilder builder = new StringBuilder();
            for (Entry entry : formArgs.entries) {
                builder.append(entry.key()).append("=").append(entry.value()).append("&");
            }
            // 转化为请求体
            return Unpooled.wrappedBuffer(builder.toString().getBytes(charset));
        } else {
            throw new TurboHttpClientException("Unsupported ContentType:" + contentType);
        }
    }

}
