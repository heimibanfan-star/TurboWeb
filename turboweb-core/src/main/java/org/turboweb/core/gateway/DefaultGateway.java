package org.turboweb.core.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.gateway.matcher.LoadBalanceRouterMatcher;
import org.turboweb.core.gateway.matcher.RoundRobinRouterMatcher;
import org.turboweb.core.http.response.HttpInfoResponse;
import org.turboweb.commons.utils.client.HttpClientUtils;
import org.turboweb.commons.utils.base.BeanUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 默认的网关实现
 */
public class DefaultGateway implements Gateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultGateway.class);
    private final LoadBalanceRouterMatcher routerMatcher;

    public DefaultGateway() {
        this(new RoundRobinRouterMatcher());
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
        // 处理响应对象到达时
        promise.addListener(future -> {
            if (!channel.isActive()) {
                return;
            }
            if (future.isSuccess()) {
                channel.writeAndFlush(future.getNow());
            } else {
                Throwable cause = future.cause();
                responseError(channel, cause);
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
                    return content.map(DefaultHttpContent::new).doFinally(signalType -> {
                        channel.eventLoop().execute(channel::close);
                    });
                } else {
                    // 增加引用
                    return content.map(buf -> {
                            buf.retain();
                            return buf;
                        })
                        .collectList().flatMap(bufList -> {
                            if (!bufList.isEmpty()) {
                                CompositeByteBuf compositeByteBuf = channel.alloc().compositeBuffer();
                                compositeByteBuf.addComponents(true, bufList);
                                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, response.status(), compositeByteBuf);
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
            // 重试失败之后对channel关闭
            .doOnError(e -> {
                if (channel.isActive()) {
                    responseError(channel, e).addListener(future -> {
                       channel.close();
                    });
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

    /**
     * 生成网关异常信息
     *
     * @param msg 信息
     * @return 异常信息
     */
    private String gatewayBadContent(String msg) {
        Map<String, String> map = new HashMap<>();
        map.put("code", String.valueOf(HttpResponseStatus.BAD_GATEWAY.code()));
        map.put("msg", msg);
        try {
            return BeanUtils.getObjectMapper().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 回写错误信息
     *
     * @param channel 管道
     * @param throwable 异常
     * @return 异步对象
     */
    private ChannelFuture responseError(Channel channel, Throwable throwable) {
        String content = gatewayBadContent(throwable.getMessage());
        HttpInfoResponse httpInfoResponse = new HttpInfoResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
        httpInfoResponse.setContent(Objects.requireNonNullElse(content, ""));
        return channel.writeAndFlush(httpInfoResponse);
    }
}
