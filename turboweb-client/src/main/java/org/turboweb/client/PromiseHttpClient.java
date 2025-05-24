package org.turboweb.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Promise;
import org.turboweb.client.result.RestResponseResult;
import org.turboweb.commons.exception.TurboSerializableException;
import org.turboweb.commons.utils.base.BeanUtils;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.function.Function;

/**
 * 返回可阻塞的promise的客户端
 */
public class PromiseHttpClient extends AbstractHttpClient {

    private final EventLoopGroup executors;

    public PromiseHttpClient(HttpClient httpClient, EventLoopGroup executors, Charset charset) {
        super(httpClient, charset);
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
    public Promise<RestResponseResult<Map>> delete(String url, Map<String, String> params) {
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
            .doOnError(promise::setFailure)
            .subscribe(response -> {
                try {
                    RestResponseResult<T> responseResult = packageResponse(response, type);
                    promise.setSuccess(responseResult);
                } catch (JsonProcessingException e) {
                    promise.setFailure(e);
                }
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
                .doOnError(promise::setFailure)
                .subscribe(response -> {
                    try {
                        RestResponseResult<T> responseResult = packageResponse(response, type);
                        promise.setSuccess(responseResult);
                    } catch (JsonProcessingException e) {
                        promise.setFailure(e);
                    }
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
     * @param forms 请求体
     * @return 返回一个promise
     */
    private <T> Promise<RestResponseResult<T>> formRequest(String url, HttpMethod method, HttpHeaders headers, Map<String, String> forms, Class<T> type) {
        Promise<RestResponseResult<T>> promise = executors.next().newPromise();
        url = buildParamUrl(url, forms);
        doFormRequest(url, method, headers, forms)
            .doOnError(promise::setFailure)
            .subscribe(response -> {
                try {
                    RestResponseResult<T> responseResult = packageResponse(response, type);
                    promise.setSuccess(responseResult);
                } catch (JsonProcessingException e) {
                    promise.setFailure(e);
                }
            });
        return promise;
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
            .doOnError(promise::setFailure)
            .subscribe(promise::setSuccess);
        return promise;
    }
}
