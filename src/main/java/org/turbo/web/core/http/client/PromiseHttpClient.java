package org.turbo.web.core.http.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Promise;
import org.apache.hc.core5.net.URIBuilder;
import org.turbo.web.core.http.client.result.RestResponseResult;
import org.turbo.web.exception.TurboHttpClientException;
import org.turbo.web.exception.TurboSerializableException;
import org.turbo.web.utils.common.BeanUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.function.Function;

/**
 * 返回可阻塞的promise的客户端
 */
public class PromiseHttpClient {

    private final HttpClient httpClient;
    private final EventLoopGroup executors;

    public PromiseHttpClient(HttpClient httpClient, EventLoopGroup executors) {
        this.httpClient = httpClient;
        this.executors = executors;
    }

    /**
     * 发起get请求
     * @param url 请求地址
     * @param headers 请求头
     * @param params 参数
     * @param type 返回类型
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> get(String url, HttpHeaders headers, Map<String, String> params, Class<T> type) {
        return doNoBodyRequest(url, HttpMethod.GET, headers, params, type);
    }

    /**
     * 发起get请求
     * @param url 请求地址
     * @param params 参数
     * @param type 返回类型
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> get(String url, Map<String, String> params, Class<T> type) {
        return get(url, null, params, type);
    }

    /**
     * 发起get请求
     * @param url 请求地址
     * @param params 参数
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<Map>> get(String url, Map<String, String> params) {
        return get(url, params, Map.class);
    }

    /**
     * 起post请求，请求格式为json
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @param type 返回类型
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> postJson(String url, HttpHeaders headers, Map<String, String> params, Object bodyContent, Class<T> type) {
        return jsonRequest(url, HttpMethod.POST, headers, params, bodyContent, type);
    }

    /**
     * 起post请求，请求格式为json
     * @param url 请求地址
     * @param bodyContent 请求体
     * @param type 返回类型
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> postJson(String url, Map<String, String> params, Object bodyContent, Class<T> type) {
        return postJson(url, null, params, bodyContent, type);
    }

    /**
     * 起post请求，请求格式为json
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @return 返回一个promise
     */
    public Promise<RestResponseResult<Map>> postJson(String url, Map<String, String> params, Object bodyContent) {
        return postJson(url, params, bodyContent, Map.class);
    }

    /**
     * 起post请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @param type 返回类型
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> postForm(String url, HttpHeaders headers, Map<String, String> params, Map<String, String> forms, Class<T> type) {
        return formRequest(url, HttpMethod.POST, headers, forms, type);
    }

    /**
     * 起post请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> postForm(String url, Map<String, String> params, Map<String, String> forms, Class<T> type) {
        return postForm(url, null, params, forms, type);
    }

    /**
     * 起post请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @return 返回一个promise
     */
    public Promise<RestResponseResult<Map>> postForm(String url, Map<String, String> params, Map<String, String> forms) {
        return postForm(url, params, forms, Map.class);
    }

    /**
     * 起put请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> putForm(String url, HttpHeaders headers, Map<String, String> params, Map<String, String> forms, Class<T> type) {
        return formRequest(url, HttpMethod.PUT, headers, forms, type);
    }

    /**
     * 起put请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> putForm(String url, Map<String, String> params, Map<String, String> forms, Class<T> type) {
        return putForm(url, null, params, forms, type);
    }

    /**
     * 起put请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @return 返回一个promise
     */
    public Promise<RestResponseResult<Map>> putForm(String url, Map<String, String> params, Map<String, String> forms) {
        return putForm(url, params, forms, Map.class);
    }

    /**
     * 起put请求，请求格式为json
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> putJson(String url, HttpHeaders headers, Map<String, String> params, Object bodyContent, Class<T> type) {
        return jsonRequest(url, HttpMethod.PUT, headers, params, bodyContent, type);
    }

    /**
     * 起put请求，请求格式为json
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> putJson(String url, Map<String, String> params, Object bodyContent, Class<T> type) {
        return putJson(url, null, params, bodyContent, type);
    }

    /**
     * 发起put请求，请求格式为json
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @return 返回一个promise
     */
    public Promise<RestResponseResult<Map>> putJson(String url, Map<String, String> params, Object bodyContent) {
        return putJson(url, params, bodyContent, Map.class);
    }

    /**
     * 发起delete操作
     * @param url 请求地址
     * @param params 参数
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> delete(String url, HttpHeaders headers, Map<String, String> params, Class<T> type) {
        return doNoBodyRequest(url, HttpMethod.DELETE, headers, params, type);
    }

    /**
     * 发起delete操作
     * @param url 请求地址
     * @param params 参数
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> delete(String url, Map<String, String> params, Class<T> type) {
        return delete(url, null, params, type);
    }

    /**
     * 发起delete操作
     * @param url 请求地址
     * @param params 参数
     * @return 返回一个promise
     */
    private Promise<RestResponseResult<Map>> delete(String url, Map<String, String> params) {
        return delete(url, params, Map.class);
    }

    /**
     * 发起不带请求体的请求
     * @param url 请求地址
     * @param params 参数
     * @return 返回一个promise
     */
    private <T> Promise<RestResponseResult<T>> doNoBodyRequest(String url, HttpMethod method, HttpHeaders headers, Map<String, String> params, Class<T> type) {
        // 拼接查询参数
        url = buildParamUrl(url, params);
        // 创建异步对象
        Promise<RestResponseResult<T>> promise = executors.next().newPromise();
        // 发起请求
        doJsonRequest(url, method, headers, null)
            .subscribe(response -> {
                packageResponsePromise(promise, response, type);
            });
        return promise;
    }

    /**
     * 发起带json请求体的请求
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @return 返回一个promise
     */
    private <T> Promise<RestResponseResult<T>> jsonRequest(String url, HttpMethod method, HttpHeaders headers, Map<String, String> params, Object bodyContent, Class<T> type) {
        Promise<RestResponseResult<T>> promise = executors.next().newPromise();
        url = buildParamUrl(url, params);
        try {
            String content = null;
            if (bodyContent != null) {
                BeanUtils.getObjectMapper().writeValueAsString(bodyContent);
            }
            doJsonRequest(url, method, headers, content)
                .subscribe(response -> {
                    packageResponsePromise(promise, response, type);
                });
            return promise;
        } catch (JsonProcessingException e) {
            throw new TurboSerializableException("序列化失败:" + e.getMessage());
        }
    }

    /**
     * 发起请求
     * @param url 请求地址
     * @param method 请求方法
     * @param headers 请求头
     * @param bodyContent 请求体
     * @return 返回一个promise
     */
    private Mono<FullHttpResponse> doJsonRequest(String url, HttpMethod method, HttpHeaders headers, String bodyContent) {
        return httpClient
            .request(method)
            .uri(url)
            .send((request, outbound) -> {
                if (headers != null) {
                    request.headers(headers);
                }
                request.header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
                if (bodyContent == null) {
                    return outbound;
                }
                return outbound.sendString(Mono.just(bodyContent));
            })
            .responseSingle((response, content) -> content.map(buf -> {
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                httpResponse.headers().add(response.responseHeaders());
                return httpResponse;
            }));
    }

    /**
     * 发起请求
     * @param url 请求地址
     * @param method 请求方法
     * @param headers 请求头
     * @param forms 请求体
     * @return 返回一个promise
     */
    private <T> Promise<RestResponseResult<T>> formRequest(String url, HttpMethod method, HttpHeaders headers, Map<String, String> forms, Class<T> type) {
        Promise<RestResponseResult<T>> promise = executors.next().newPromise();
        url = buildParamUrl(url, forms);
        doFormRequest(url, method, headers, forms)
            .subscribe(response -> {
                packageResponsePromise(promise, response, type);
            });
        return promise;
    }

    /**
     * 发起请求
     * @param url 请求地址
     * @param method 请求方法
     * @param headers 请求头
     * @param forms 请求体
     * @return 返回一个promise
     */
    private Mono<FullHttpResponse> doFormRequest(String url, HttpMethod method, HttpHeaders headers, Map<String, String> forms) {
        return httpClient
            .request(method)
            .uri(url)
            .sendForm((request, form) -> {
                if (headers != null) {
                    request.headers(headers);
                }
                request.header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED);
                if (forms != null && !forms.isEmpty()) {
                    forms.forEach(form::attr);
                }
            })
            .responseSingle((response, content) -> content.map(buf -> {
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                httpResponse.headers().add(response.responseHeaders());
                return httpResponse;
            }));
    }

    /**
     * 构建参数url
     * @param url 请求地址
     * @param params 参数
     * @return 返回一个promise
     */
    private String buildParamUrl(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        try {
            URIBuilder uriBuilder = new URIBuilder(url);
            params.forEach(uriBuilder::setParameter);
            return uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new TurboHttpClientException("无效的url:" + url);
        }
    }

    /**
     * 发起请求
     * @param function 请求方法
     * @return 返回一个promise
     */
    public <T> Promise<FullHttpResponse> request(Function<HttpClient, HttpClient.ResponseReceiver<?>> function) {
        Promise<FullHttpResponse> promise = executors.next().newPromise();
        function.apply(httpClient)
            .responseSingle((response, content) -> content.map(buf -> {
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                httpResponse.headers().add(response.responseHeaders());
                return httpResponse;
            }))
            .subscribe(promise::setSuccess);
        return promise;
    }

    /**
     * 封装响应的promise
     * @param response 响应
     * @param type 返回类型
     */
    private <T> void packageResponsePromise(Promise<RestResponseResult<T>> promise, FullHttpResponse response, Class<T> type) {
        long contentLen = Long.parseLong(response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        String responseContent;
        if (contentLen > 0) {
            responseContent = response.content().toString(Charset.defaultCharset());
        } else {
            responseContent = "{}";
        }
        try {
            T value = BeanUtils.getObjectMapper().readValue(responseContent, type);
            promise.setSuccess(new RestResponseResult<>(response.headers(), value));
        } catch (JsonProcessingException e) {
            promise.setFailure(e);
        }
    }
}
