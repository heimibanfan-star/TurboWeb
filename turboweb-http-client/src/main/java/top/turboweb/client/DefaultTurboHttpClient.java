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
 * 默认的http客户端
 */
public class DefaultTurboHttpClient implements TurboHttpClient {

    private final Converter converter;
    private final HttpClientEngine httpClientEngine;
    // 请求拦截器
    private final List<RequestInterceptor> requestInterceptors = new ArrayList<>();
    // 响应拦截器
    private final List<ResponseInterceptor> responseInterceptors = new ArrayList<>();


    public DefaultTurboHttpClient(HttpClientEngine engine, Converter converter) {
        this.httpClientEngine = engine;
        this.converter = converter;
    }

    public DefaultTurboHttpClient(HttpClientEngine engine) {
        this(engine, new JsonConverter());
    }

    public DefaultTurboHttpClient(String baseUrl) {
        this(new HttpClientEngine(baseUrl));
    }

    public DefaultTurboHttpClient() {
        this(new HttpClientEngine(""));
    }

    @Override
    public ClientResult request(String path, HttpMethod method, Object data, Consumer<Config> consumer) {
        Config config = new Config();
        consumer.accept(config);
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
    public ClientResult delete(String path) {
        return request(path, HttpMethod.DELETE);
    }

    @Override
    public ClientResult delete(String path, Consumer<Config> consumer) {
        return request(path, HttpMethod.DELETE, consumer);
    }

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
     * 构建请求
     * @param path 请求路径
     * @param method 请求方式
     * @param data 请求数据
     * @param config 请求配置
     * @return HttpRequest
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
     * 构建请求体
     * @param data 请求数据
     * @param config 请求配置
     * @return bytebuf
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
