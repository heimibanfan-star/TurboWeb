package top.turboweb.client.engine;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.*;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.SslProvider;
import top.turboweb.commons.exception.TurboHttpClientException;

import java.io.Closeable;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * HTTP 客户端引擎。
 * <p>
 * 基于 Reactor Netty 实现，提供同步和异步 HTTP 请求发送能力。
 * 支持自定义线程数、超时时间、连接池配置、HTTP 协议版本和 SSL 配置。
 * <p>
 * 特性：
 * <ul>
 *     <li>支持阻塞同步请求 {@link #send(HttpRequest)} 与异步请求 {@link #sendAsync(HttpRequest)}</li>
 *     <li>支持自定义基础 URL</li>
 *     <li>支持全局连接池和线程池配置</li>
 *     <li>支持 SSL/TLS 自定义配置</li>
 * </ul>
 * <p>
 * 使用场景：
 * <ul>
 *     <li>作为 {@link top.turboweb.client.DefaultTurboHttpClient} 的底层请求引擎</li>
 *     <li>需要高性能 HTTP 调用、连接复用和异步处理的场景</li>
 * </ul>
 */
public class HttpClientEngine implements Closeable {

    private final HttpClient httpClient;
    private final EventLoopGroup group;
    private final String baseUrl;
    private static final ByteBuf EMPTY_BUF = Unpooled.EMPTY_BUFFER;

    public final static class Config {
        // 线程数量
        int threadNum = -1;
        // 基础url
        String baseUrl = "";
        // 实例名称
        String name = "TurboWebHttpClient";
        // 超时时间
        long timeout = -1;
        // 连接配置器
        Consumer<ConnectionProvider.Builder> connectConsumer;
        // ssl配置
        Consumer<? super SslProvider.SslContextSpec> sslConsumer;

        public Config ioThread(int num) {
            this.threadNum = num;
            return this;
        }

        public Config baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl can not be null");
            return this;
        }

        public Config name(String name) {
            this.name = Objects.requireNonNull(name, "name can not be null");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("name can not be empty");
            }
            return this;
        }

        public Config timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public Config connect(Consumer<ConnectionProvider.Builder> consumer) {
            this.connectConsumer = consumer;
            return this;
        }

        public Config ssl(Consumer<? super SslProvider.SslContextSpec> consumer) {
            this.sslConsumer = consumer;
            return this;
        }
    }

    public HttpClientEngine(int threadNum, String baseUrl, String name, Consumer<ConnectionProvider.Builder> consumer) {
        this(config -> {
            config
                    .ioThread(threadNum)
                    .baseUrl(baseUrl)
                    .name(name)
                    .connect(consumer);
        });
    }

    public HttpClientEngine(int threadNum, String baseUrl) {
        this(config -> {
           config
                   .ioThread(threadNum)
                   .baseUrl(baseUrl)
                   .name("TurboWebHttpClient");
        });
    }

    public HttpClientEngine(String baseUrl) {
        this(config -> {
            config
                    .baseUrl(baseUrl)
                    .ioThread(1)
                    .name("TurboWebHttpClient");
        });
    }


    public HttpClientEngine(Consumer<Config> consumer) {
        Config config = new Config();
        consumer.accept(config);
        String baseUrl = Objects.requireNonNull(config.baseUrl, "baseUrl can not be null");
        // 处理路径
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // 创建事件循环组
        if (config.threadNum <= 0) {
            config.ioThread(1);
        }
        this.group = new NioEventLoopGroup(config.threadNum);
        // 进行连接的配置
        ConnectionProvider.Builder builder = ConnectionProvider.builder(Objects.requireNonNull(config.name, "name can not be null"));
        if (config.connectConsumer != null) {
            config.connectConsumer.accept(builder);
        }
        // 创建http客户端
        HttpClient httpClient = HttpClient.create(builder.build()).protocol(HttpProtocol.HTTP11);
        // 判断是否配置协议
        // 判断是否配置ssl证书
        if (config.sslConsumer != null) {
            httpClient = httpClient.secure(config.sslConsumer);
        }
        // 处理超时时间
        if (config.timeout > 0) {
            httpClient = httpClient.responseTimeout(Duration.ofMillis(config.timeout));
        }
        // 设置客户端
        this.httpClient = httpClient;
    }


    /**
     * 发送同步 HTTP 请求
     *
     * @param request 请求对象
     * @return 响应对象
     * @throws TurboHttpClientException 如果响应为空
     */
    public HttpResponse send(HttpRequest request) {
        Mono<HttpResponse> mono = sendAsync(request);
        // 阻塞等待结果
        HttpResponse response = mono.block();
        if (response == null) {
            throw new TurboHttpClientException("HttpClientEngine sendAsync return null");
        }
        return response;
    }

    /**
     * 异步发送 HTTP 请求
     *
     * @param request 请求对象
     * @return {@link Mono} 响应对象
     */
    public Mono<HttpResponse> sendAsync(HttpRequest request) {
        String uri;
        if (request.uri().startsWith("http://") || request.uri().startsWith("https://")) {
            uri = request.uri();
        } else {
            uri = request.uri().startsWith("/") ? request.uri() : "/" + request.uri();
        }
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
                        return outbound.send(Mono.just(buf.retain()).doFinally(signalType -> buf.release()));
                    }
                    // 不是携带请求体的请求
                    return outbound.send(Mono.empty());
                })
                .responseSingle((response, content) -> {
                    // 获取响应头
                    HttpHeaders headers = response.responseHeaders();
                    // 获取请求体
                    return content
                            .switchIfEmpty(Mono.just(EMPTY_BUF))
                            .map(buf -> {
                                if (buf == EMPTY_BUF) {
                                    return new DefaultHttpResponse(HttpVersion.HTTP_1_1, response.status(), headers);
                                } else {
                                    // 增加引用计数
                                    buf.retain();
                                    // 创建响应对象
                                    DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, response.status(), buf);
                                    httpResponse.headers().set(headers);
                                    return httpResponse;
                                }
                            });
                });
    }

    /**
     * 关闭客户端，释放线程池资源。
     * <p>
     * 调用后不应再发送请求。
     */
    @Override
    public void close() {
        group.shutdownGracefully();
    }
}
