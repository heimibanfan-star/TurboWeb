package top.turboweb.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import reactor.core.publisher.Mono;
import top.turboweb.client.builder.FormBodyBuilder;
import top.turboweb.client.builder.HttpBaseBuilder;
import top.turboweb.client.builder.JsonBodyBuilder;
import top.turboweb.client.config.HttpClientConfig;
import reactor.netty.http.client.HttpClient;
import top.turboweb.client.result.RestResponseResult;
import top.turboweb.commons.exception.TurboHttpClientException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * http客户端实现类
 */
public class TurboHttpClient {

    private final HttpClient httpClient;
    private final EventLoop executors;

    public TurboHttpClient(HttpClient httpClient, EventLoop executors) {
        this.httpClient = httpClient;
        this.executors = executors;
    }

    /**
     * 发送http请求
     * @param httpBaseBuilder http请求构造器
     * @return 响应的结果
     */
    public FullHttpResponse request(HttpBaseBuilder httpBaseBuilder) {
        return awaitResponse(sendRequest(httpBaseBuilder));
    }

    /**
     * 阻塞异步对象，等待响应结果的返回
     * @param promise 异步对象
     * @return 响应结果
     */
    private FullHttpResponse awaitResponse(Promise<FullHttpResponse> promise) {
        try {
            return promise.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new TurboHttpClientException(e);
        }
    }

    /**
     * 发送http请求
     * @param httpBaseBuilder http请求的信息构造器
     * @return 异步的响应结果
     */
    private Promise<FullHttpResponse> sendRequest(HttpBaseBuilder httpBaseBuilder) {
        Promise<FullHttpResponse> promise = new DefaultPromise<>(executors);
        // 设置http请求的基本信息：请求头、请求地址。
        HttpClient.RequestSender requestSender = httpClient
                .headers(header -> {
                    header.add(httpBaseBuilder.getHeaders());
                })
                .request(httpBaseBuilder.getHttpMethod())
                .uri(httpBaseBuilder.buildUrl());
        HttpClient.ResponseReceiver<?> responseReceiver;
        // 处理请求体的格式为application/json
        if (httpBaseBuilder instanceof JsonBodyBuilder jsonBodyBuilder) {
            responseReceiver = requestSender.send((request, outbound) -> {
                request.header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
                String jsonContent = jsonBodyBuilder.getJsonContent();
                if (jsonContent == null) {
                    return outbound;
                }
                return outbound.sendString(Mono.just(jsonContent));
            });
        }
        // 处理非application/json格式的请求体
        else if (requestSender instanceof FormBodyBuilder formBodyBuilder) {
            responseReceiver = requestSender.sendForm((request, form) -> {
                List<HttpBaseBuilder.ParamEntity<String>> formParams = formBodyBuilder.getFormParams();
                if (!formParams.isEmpty()) {
                    formParams.forEach(entity -> {
                        form.attr(entity.key(), entity.value());
                    });
                }
            });
        } else {
            responseReceiver = requestSender.send((request, outbound) -> outbound);
        }
        // 接受到响应结果，封装为fullHttpResponse并通知promise
        responseReceiver
                .responseSingle((response, content) -> content.map(buf -> {
                    FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                    httpResponse.headers().add(response.responseHeaders());
                    return httpResponse;
                }))
                .subscribe(response -> {
                    promise.setSuccess(response);
                    response.retain();
                }, promise::setFailure);
        return promise;
    }
}
