package top.turboweb.client.builder;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户设置http请求基础属性的构建器
 */
public class HttpBaseBuilder {

    /**
     * 参数实体
     */
    public record ParamEntity<T>(String key, T value) { }

    /**
     * 请求的url地址
     */
    protected final String url;
    /**
     * 存储url请求参数
     */
    protected final List<ParamEntity<Object>> urlParams = new ArrayList<>();
    /**
     * 请求类型
     */
    protected final HttpMethod httpMethod;
    /**
     * 请求头
     */
    protected HttpHeaders headers = new DefaultHttpHeaders();

    public HttpBaseBuilder(HttpMethod method, String url) {
        Objects.requireNonNull(method);
        Objects.requireNonNull(url);
        this.httpMethod = method;
        this.url = url;
    }

    /**
     * 添加请求的url参数
     * @param key 参数的键
     * @param value 参数的值
     */
    public void addUrlParam(String key, Object value) {
        ParamEntity<Object> entity = new ParamEntity<>(key, value);
        urlParams.add(entity);
    }

    /**
     * 添加多个请求的url参数
     * @param params 存储url参数的map集合
     */
    public void addUrlParams(Map<String, Object> params) {
        for (Map.Entry<String, Object> mapEntry : params.entrySet()) {
            ParamEntity<Object> entity = new ParamEntity<>(mapEntry.getKey(), mapEntry.getValue());
            urlParams.add(entity);
        }
    }

    /**
     * 用新的请求头替换掉内部的请求头
     * @param headers 请求头
     */
    public void setHeaders(HttpHeaders headers) {
        if (headers != null) {
            this.headers = headers;
        }
    }

    /**
     * 获取请求头，便于对请求头的内容进行设置
     * @return 请求头
     */
    public HttpHeaders getHeaders() {
        return this.headers;
    }

    /**
     * 获取请求方式
     * @return 请求方式的类型
     */
    public HttpMethod getHttpMethod() {
        return this.httpMethod;
    }

    /**
     * 结合请求的url参数生成真正的url地址
     * @return 携带参数的url地址
     */
    public String buildUrl() {
        if (urlParams.isEmpty()) {
            return this.url;
        }
        // 构造url
        StringBuilder urlBuilder = new StringBuilder(this.url);
        if (url.contains("?")) {
            if (!url.endsWith("?")) {
                urlBuilder.append("&");
            }
        } else {
            urlBuilder.append("?");
        }
        // 拼接参数
        for (ParamEntity<Object> urlParam : urlParams) {
            urlBuilder
                    .append(URLEncoder.encode(urlParam.key, StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(urlParam.value.toString(), StandardCharsets.UTF_8))
                    .append("&");
        }
        return urlBuilder.substring(0, urlBuilder.length() - 1);
    }
}
