package top.turboweb.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.gateway.matcher.LoadBalanceRouterMatcher;
import top.turboweb.gateway.matcher.RoundRobinRouterMatcher;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.commons.utils.base.BeanUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 默认的网关实现
 */
public class DefaultGateway implements Gateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultGateway.class);
    private final LoadBalanceRouterMatcher routerMatcher;
    private HttpClient httpClient;

    public DefaultGateway() {
        this(new RoundRobinRouterMatcher());
    }

    public DefaultGateway(LoadBalanceRouterMatcher loadBalanceRouterMatcher) {
        routerMatcher = loadBalanceRouterMatcher;
    }

    @Override
    public void setHttpClient(HttpClient httpClient) {
        Objects.requireNonNull(httpClient, "httpClient can not be null");
        if (this.httpClient == null) {
            this.httpClient = httpClient;
        }
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
        AtomicBoolean sendStarted = new AtomicBoolean(false);
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
                    // 写入响应头
                    HttpResponse toWriteResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, response.status());
                    toWriteResponse.headers().set(response.responseHeaders());
                    // 写入响应头
                    channel.writeAndFlush(toWriteResponse);
                    sendStarted.set(true);
                    return content;
                })
                .map(DefaultHttpContent::new)
                // 触发订阅，将sse内容写入channel
                .subscribe(
                        httpContent -> {
                            httpContent.retain();
                            channel.writeAndFlush(httpContent);
                        },
                        e -> {
                            log.error("Gateway error", e);
                            // 判断数据是否已经写入
                            if (sendStarted.get()) {
                                // 直接关闭
                                channel.close();
                            } else {
                                responseError(channel, e).addListener(future -> {
                                    channel.close();
                                });
                            }
                        },
                        () -> {
                            channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
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
     * @param channel   管道
     * @param throwable 异常
     * @return 异步对象
     */
    private ChannelFuture responseError(Channel channel, Throwable throwable) {
        String content = gatewayBadContent(throwable.getMessage());
        HttpInfoResponse httpInfoResponse = new HttpInfoResponse(HttpResponseStatus.BAD_GATEWAY);
        httpInfoResponse.setContent(Objects.requireNonNullElse(content, ""));
        return channel.writeAndFlush(httpInfoResponse);
    }
}
