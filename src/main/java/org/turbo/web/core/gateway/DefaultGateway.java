package org.turbo.web.core.gateway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.concurrent.Promise;
import org.turbo.web.core.gateway.matcher.LoadBalanceRouterMatcher;
import org.turbo.web.core.gateway.matcher.RoundRobinRouterMatcher;
import org.turbo.web.utils.client.HttpClientUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;

/**
 * 默认的网关实现
 */
public class DefaultGateway implements Gateway {

    private final LoadBalanceRouterMatcher routerMatcher;
    private final int retryNum;
    private final int retryInterval;

    public DefaultGateway() {
        this(1, 100);
    }

    public DefaultGateway(int retryNum, int retryInterval) {
        this(new RoundRobinRouterMatcher(), retryNum, retryInterval);
    }

    public DefaultGateway(LoadBalanceRouterMatcher loadBalanceRouterMatcher, int retryNum, int retryInterval) {
        routerMatcher = loadBalanceRouterMatcher;
        this.retryNum = retryNum;
        this.retryInterval = retryInterval;
    }


    @Override
    public void addServerNode(String prefix, String... urls) {
        routerMatcher.addServiceNode(prefix, urls);
    }

    @Override
    public String matchNode(String uri) {
        return routerMatcher.matchNode(uri);
    }

    @Override
    public void forwardRequest(String url, FullHttpRequest fullHttpRequest, Channel channel) {
        HttpClient httpClient = HttpClientUtils.httpClient();
        // 创建异步回调对象
        Promise<HttpResponse> promise = channel.eventLoop().newPromise();
        // 处理响应对象到达时
        promise.addListener(future -> {
            if (future.isSuccess()) {
                channel.writeAndFlush(future.get());
            } else {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
                channel.writeAndFlush(response);
            }
        });
        httpClient
            .request(fullHttpRequest.method())
            .uri(url)
            // 设置需要发送的数据
            .send((request, outbound) -> {
                request.headers(fullHttpRequest.headers());
                return outbound.send(Mono.just(fullHttpRequest.content()));
            })
            // 处理响应结果
            .response((response, content) -> {
                HttpHeaders headers = response.responseHeaders();
                String contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
                if (Objects.equals(contentType, "text/event-stream")) {
                    HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    httpResponse.headers().add(response.responseHeaders());
                    promise.setSuccess(httpResponse);
                    // 处理sse推送结束
                    return content.map(DefaultHttpContent::new).doOnComplete(() -> channel.eventLoop().execute(channel::close));
                } else {
                    // 增加引用
                    return content.map(buf -> {
                            buf.retain();
                            return buf;
                        })
                        .collectList().flatMap(bufList -> {
                            if (!bufList.isEmpty()) {
                                // 如果包含请求体正常写入内容
                                ByteBuf buf = bufList.getFirst();
                                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, response.status(), buf);
                                httpResponse.headers().add(response.responseHeaders());
                                promise.setSuccess(httpResponse);
                            } else {
                                // 如果没有信号直接返回空数据
                                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, response.status());
                                httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
                                promise.setSuccess(httpResponse);
                            }
                            return Mono.empty();
                        });
                }
            })
            // 出现异常时触发重试机制
            .retryWhen(
                Retry.backoff(retryNum, Duration.ofMillis(retryInterval))
                    .filter(e -> (e instanceof TimeoutException) || (e instanceof ConnectTimeoutException))
            )
            // 当远程通道关闭时关闭自身通道
            .doOnError(e -> {
                if (e instanceof PrematureCloseException) {
                    if (channel.isActive()) {
                        channel.close();
                    }
                }
            })
            // 触发订阅，将sse内容写入channel
            .subscribe(
                httpContent -> {
                    httpContent.retain();
                    channel.eventLoop().execute(() -> {
                        channel.writeAndFlush(httpContent);
                    });
                }
            );

    }
}
