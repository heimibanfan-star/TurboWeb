package org.turboweb.commons.utils.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.*;
import org.turboweb.commons.utils.client.result.RestResponseResult;
import org.turboweb.commons.exception.TurboSerializableException;
import org.turboweb.commons.utils.common.BeanUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.function.Function;

/**
 * 反应式http客户端
 */
public class ReactiveHttpClient extends AbstractHttpClient {

    public ReactiveHttpClient(HttpClient httpClient, Charset charset) {
        super(httpClient, charset);
    }

    /**
     * 发起get请求
     * @param url 请求地址
     * @param headers 请求头
     * @param params 参数
     * @param type 返回类型
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> get(String url, HttpHeaders headers, Map<String, String> params, Class<T> type) {
        return doNoBodyRequest(url, HttpMethod.GET, headers, params, type);
    }

    /**
     * 发起get请求
     * @param url 请求地址
     * @param params 参数
     * @param type 返回类型
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> get(String url, Map<String, String> params, Class<T> type) {
        return get(url, null, params, type);
    }

    /**
     * 发起get请求
     * @param url 请求地址
     * @param params 参数
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<Map>> get(String url, Map<String, String> params) {
        return get(url, params, Map.class);
    }

    /**
     * 起post请求，请求格式为json
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @param type 返回类型
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> postJson(String url, HttpHeaders headers, Map<String, String> params, Object bodyContent, Class<T> type) {
        return jsonRequest(url, HttpMethod.POST, headers, params, bodyContent, type);
    }

    /**
     * 起post请求，请求格式为json
     * @param url 请求地址
     * @param bodyContent 请求体
     * @param type 返回类型
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> postJson(String url, Map<String, String> params, Object bodyContent, Class<T> type) {
        return postJson(url, null, params, bodyContent, type);
    }

    /**
     * 起post请求，请求格式为json
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @return 返回反应式流
     */
    public Mono<RestResponseResult<Map>> postJson(String url, Map<String, String> params, Object bodyContent) {
        return postJson(url, params, bodyContent, Map.class);
    }

    /**
     * 起post请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @param type 返回类型
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> postForm(String url, HttpHeaders headers, Map<String, String> params, Map<String, String> forms, Class<T> type) {
        return formRequest(url, HttpMethod.POST, headers, forms, type);
    }

    /**
     * 起post请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> postForm(String url, Map<String, String> params, Map<String, String> forms, Class<T> type) {
        return postForm(url, null, params, forms, type);
    }

    /**
     * 起post请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @return 返回反应式流
     */
    public Mono<RestResponseResult<Map>> postForm(String url, Map<String, String> params, Map<String, String> forms) {
        return postForm(url, params, forms, Map.class);
    }

    /**
     * 起put请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> putForm(String url, HttpHeaders headers, Map<String, String> params, Map<String, String> forms, Class<T> type) {
        return formRequest(url, HttpMethod.PUT, headers, forms, type);
    }

    /**
     * 起put请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> putForm(String url, Map<String, String> params, Map<String, String> forms, Class<T> type) {
        return putForm(url, null, params, forms, type);
    }

    /**
     * 起put请求，请求格式为form
     * @param url 请求地址
     * @param params 参数
     * @param forms 请求体
     * @return 返回反应式流
     */
    public Mono<RestResponseResult<Map>> putForm(String url, Map<String, String> params, Map<String, String> forms) {
        return putForm(url, params, forms, Map.class);
    }

    /**
     * 起put请求，请求格式为json
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> putJson(String url, HttpHeaders headers, Map<String, String> params, Object bodyContent, Class<T> type) {
        return jsonRequest(url, HttpMethod.PUT, headers, params, bodyContent, type);
    }

    /**
     * 起put请求，请求格式为json
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> putJson(String url, Map<String, String> params, Object bodyContent, Class<T> type) {
        return putJson(url, null, params, bodyContent, type);
    }

    /**
     * 发起put请求，请求格式为json
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @return 返回反应式流
     */
    public Mono<RestResponseResult<Map>> putJson(String url, Map<String, String> params, Object bodyContent) {
        return putJson(url, params, bodyContent, Map.class);
    }

    /**
     * 发起delete操作
     * @param url 请求地址
     * @param params 参数
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> delete(String url, HttpHeaders headers, Map<String, String> params, Class<T> type) {
        return doNoBodyRequest(url, HttpMethod.DELETE, headers, params, type);
    }

    /**
     * 发起delete操作
     * @param url 请求地址
     * @param params 参数
     * @return 返回反应式流
     */
    public <T> Mono<RestResponseResult<T>> delete(String url, Map<String, String> params, Class<T> type) {
        return delete(url, null, params, type);
    }

    /**
     * 发起delete操作
     * @param url 请求地址
     * @param params 参数
     * @return 返回反应式流
     */
    private Mono<RestResponseResult<Map>> delete(String url, Map<String, String> params) {
        return delete(url, params, Map.class);
    }

    /**
     * 发起不带请求体的请求
     * @param url 请求地址
     * @param params 参数
     * @return 返回反应式流
     */
    private <T> Mono<RestResponseResult<T>> doNoBodyRequest(String url, HttpMethod method, HttpHeaders headers, Map<String, String> params, Class<T> type) {
        // 拼接查询参数
        url = buildParamUrl(url, params);
        // 发起请求
        return doJsonRequest(url, method, headers, null)
            .flatMap(response -> {
                try {
                    RestResponseResult<T> responseResult = packageResponse(response, type);
                    return Mono.just(responseResult);
                } catch (JsonProcessingException e) {
                    return Mono.error(e);
                }
            });
    }


    /**
     * 发起带json请求体的请求
     * @param url 请求地址
     * @param params 参数
     * @param bodyContent 请求体
     * @return 返回反应式流
     */
    private <T> Mono<RestResponseResult<T>> jsonRequest(String url, HttpMethod method, HttpHeaders headers, Map<String, String> params, Object bodyContent, Class<T> type) {
        url = buildParamUrl(url, params);
        try {
            String content = null;
            if (bodyContent != null) {
                content = BeanUtils.getObjectMapper().writeValueAsString(bodyContent);
            }
            return doJsonRequest(url, method, headers, content)
                .flatMap(response -> {
                    try {
                        RestResponseResult<T> responseResult = packageResponse(response, type);
                        return Mono.just(responseResult);
                    } catch (JsonProcessingException e) {
                        return Mono.error(e);
                    }
                });
        } catch (JsonProcessingException e) {
            return Mono.error(new TurboSerializableException("序列化失败:" + e.getMessage()));
        }
    }

    /**
     * 发起请求
     * @param url 请求地址
     * @param method 请求方法
     * @param headers 请求头
     * @param forms 请求体
     * @return 返回反应式流
     */
    private <T> Mono<RestResponseResult<T>> formRequest(String url, HttpMethod method, HttpHeaders headers, Map<String, String> forms, Class<T> type) {
        url = buildParamUrl(url, forms);
        return doFormRequest(url, method, headers, forms)
            .flatMap(response -> {
                try {
                    RestResponseResult<T> responseResult = packageResponse(response, type);
                    return Mono.just(responseResult);
                } catch (JsonProcessingException e) {
                    return Mono.error(e);
                }
            });
    }

    /**
     * 发起请求
     *
     * @param function 请求方法
     * @return 返回反应式流
     */
    public <T> Mono<FullHttpResponse> request(Function<HttpClient, HttpClient.ResponseReceiver<?>> function) {
        return function.apply(httpClient)
            .responseSingle((response, content) -> content.map(buf -> {
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                httpResponse.headers().add(response.responseHeaders());
                return httpResponse;
            }));
    }
}
