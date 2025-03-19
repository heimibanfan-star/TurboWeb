package org.turbo.web.core.gateway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Promise;
import org.turbo.web.core.gateway.matcher.LoadBalanceRouterMatcher;
import org.turbo.web.core.gateway.matcher.RoundRobinRouterMatcher;
import org.turbo.web.utils.client.HttpClientUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.PrematureCloseException;

import java.util.Objects;

/**
 * 默认的网关实现
 */
public class DefaultGateway implements Gateway{

    private final LoadBalanceRouterMatcher routerMatcher;

    public DefaultGateway() {
        routerMatcher = new RoundRobinRouterMatcher();
    }

    public DefaultGateway(LoadBalanceRouterMatcher loadBalanceRouterMatcher) {
        routerMatcher = loadBalanceRouterMatcher;
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
            .send((request, outbound) -> {
                request.headers(fullHttpRequest.headers());
                return outbound.send(Mono.just(fullHttpRequest.content()));
            })
            .response((response, content) -> {
                HttpHeaders headers = response.responseHeaders();
                String contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
                if (Objects.equals(contentType, "text/event-stream")) {
                    HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    httpResponse.headers().add(response.responseHeaders());
                    promise.setSuccess(httpResponse);
                    return content.map(DefaultHttpContent::new);
                } else {
                    return content.map(buf -> {
                            buf.retain();
                            return buf;
                        })
                        .collectList().flatMap(bufList -> {
                            ByteBuf buf = bufList.getFirst();
                            FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                            httpResponse.headers().add(response.responseHeaders());
                            promise.setSuccess(httpResponse);
                            return Mono.empty();
                        });
                }
            })
            .doOnError(e -> {
                if (e instanceof PrematureCloseException) {
                    channel.close();
                }
            })
            .subscribe(httpContent -> {
               httpContent.retain();
               channel.eventLoop().execute(() -> {
                   channel.writeAndFlush(httpContent);
               });
            });

    }
}
