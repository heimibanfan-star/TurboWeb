package org.turbo.web.core.http.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Promise;
import org.turbo.web.core.http.client.result.RestResponseResult;
import org.turbo.web.utils.common.BeanUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.Charset;

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
     * 发起请求
     * @param url 请求地址
     * @param method 请求方法
     * @param headers 请求头
     * @param bodyContent 请求体
     * @param type 返回类型
     * @return 返回一个promise
     */
    public <T> Promise<RestResponseResult<T>> get(String url, HttpMethod method, HttpHeaders headers, String bodyContent, Class<T> type) {
        // 创建异步对象
        Promise<RestResponseResult<T>> promise = executors.next().newPromise();
        // 发起请求
        doRequest(url, method, headers, bodyContent)
            .subscribe(response -> {
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
            });
        return promise;
    }

    /**
     * 发起请求
     * @param url 请求地址
     * @param method 请求方法
     * @param headers 请求头
     * @param bodyContent 请求体
     * @return 返回一个promise
     */
    private Mono<FullHttpResponse> doRequest(String url, HttpMethod method, HttpHeaders headers, String bodyContent) {
        return httpClient
            .request(method)
            .uri(url)
            .send((request, outbound) -> {
                if (headers != null) {
                    request.headers(headers);
                }
                request.header(HttpHeaderNames.CONTENT_TYPE, "application/json");
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
}
