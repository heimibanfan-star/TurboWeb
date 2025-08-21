package top.turboweb.client.engine;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Promise;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * http客户端引擎
 */
public class HttpClientEngine implements Closeable {

    private final HttpClient httpClient;
    private final EventLoopGroup group;
    private final String baseUrl;

    public HttpClientEngine(int threadNum, String baseUrl, String name, Consumer<ConnectionProvider.Builder> consumer) {
        Objects.requireNonNull(baseUrl, "baseUrl can not be null");
        // 去除末尾的/
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        group = new NioEventLoopGroup(threadNum);
        ConnectionProvider.Builder builder = ConnectionProvider.builder(name);
        consumer.accept(builder);
        httpClient = HttpClient.create(builder.build()).runOn(group);
    }

    public HttpClientEngine(int threadNum, String baseUrl, String name) {
        this(threadNum, baseUrl, name, builder -> {
        });
    }

    public HttpClientEngine(int threadNum, String baseUrl) {
        this(threadNum, baseUrl, "httpClientEngine");
    }

    public HttpClientEngine(String baseUrl) {
        this(1, baseUrl);
    }

    /**
     * 发送请求
     *
     * @param request 请求
     * @return 响应
     */
    public HttpResponse send(HttpRequest request) {
        Promise<HttpResponse> promise = group.next().newPromise();
        Flux<HttpResponse> flux = doSend(request);
        flux.subscribe(new BaseSubscriber<HttpResponse>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                // 订阅一个元素
                request(1);
            }

            @Override
            protected void hookOnNext(HttpResponse value) {
                promise.setSuccess(value);
                // 取消订阅
                cancel();
            }

            @Override
            protected void hookOnError(Throwable throwable) {
                promise.setFailure(throwable);
            }
        });
        try {
            return promise.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private Flux<HttpResponse> doSend(HttpRequest request) {
        String uri = request.uri().startsWith("/") ? request.uri() : "/" + request.uri();
        return httpClient
                .request(request.method())
                .uri(baseUrl + uri)
                .send((httpClientRequest, outbound) -> {
                    // 设置请求头
                    httpClientRequest.headers(request.headers());
                    // 判断是否是携带请求体的请求
                    if (request instanceof FullHttpRequest fullHttpRequest) {
                        // 获取请求体
                        ByteBuf buf = fullHttpRequest.content();
                        // 发送请求体
                        return outbound.send(Mono.just(buf));
                    }
                    // 不是携带请求体的请求
                    return outbound.send(Mono.empty());
                })
                .response((response, content) -> {
                    // 获取响应头
                    HttpHeaders headers = response.responseHeaders();
                    // 对响应体增加引用
                    return content.map(buf -> {
                                buf.retain();
                                return buf;
                            })
                            .collectList()
                            .flatMap(bufList -> {
                                if ((bufList.isEmpty())) {
                                    return Mono.just(new DefaultHttpResponse(HttpVersion.HTTP_1_1, response.status(), headers));
                                }
                                // 聚合流
                                CompositeByteBuf compositeByteBuf = UnpooledByteBufAllocator.DEFAULT.compositeBuffer();
                                compositeByteBuf.addComponents(true, bufList);
                                DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, response.status(), compositeByteBuf);
                                fullHttpResponse.headers().set(headers);
                                return Mono.just(fullHttpResponse);
                            });

                });
    }

    @Override
    public void close() throws IOException {
        group.shutdownGracefully();
    }
}
