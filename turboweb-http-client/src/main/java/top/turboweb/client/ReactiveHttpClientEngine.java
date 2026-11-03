package top.turboweb.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;
import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.Headers;
import top.turboweb.http.core.HttpRequestMessage;
import top.turboweb.http.core.StatusLine;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 reactor-netty 响应式客户端的引擎实现：构造期按 {@link ReactiveClientConfig}
 * 一次性装配共享的 {@link HttpClient}，聚合与流式两入口共用同一传输逻辑——
 * {@link #request(HttpRequestMessage)} 挂聚合回调经 {@link #stream} 发起，阻塞等待终态后构造消息。
 *
 * <p>事件循环组恒由引擎按 eventLoopCount 自建自管；连接池未透传时按配置数值内部装配，
 * 透传则直接使用。{@link #close()} 只释放引擎自建的资源，透传的连接池不归引擎释放。
 * 引擎关闭后不可再发起请求。</p>
 */
public class ReactiveHttpClientEngine implements HttpClientEngine {

    private static final String RESOURCE_NAME = "turboweb-http-client";
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ConnectionProvider connectionProvider;
    private final LoopResources loopResources;
    private final boolean ownConnectionProvider;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 以给定配置装配引擎：连接池、事件循环组与客户端各配置项在此一次性定型。
     *
     * @param config 传输配置，不能为空
     * @throws NullPointerException 当 {@code config} 为 {@code null} 时抛出
     */
    public ReactiveHttpClientEngine(ReactiveClientConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.ownConnectionProvider = config.connectionProvider() == null;
        this.connectionProvider = ownConnectionProvider
                ? buildConnectionProvider(config)
                : config.connectionProvider();
        this.loopResources = LoopResources.create(RESOURCE_NAME, config.eventLoopCount(), true);

        HttpClient client = HttpClient.create(connectionProvider)
                .runOn(loopResources, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.connectTimeout().toMillis());
        if (config.responseTimeout() != null) {
            client = client.responseTimeout(config.responseTimeout());
        }
        if (config.compression()) {
            client = client.compress(true);
        }
        if (config.followRedirect()) {
            client = client.followRedirect(true);
        }
        if (config.wiretap()) {
            client = client.wiretap(true);
        }
        if (config.sslProvider() != null) {
            client = client.secure(config.sslProvider());
        }
        if (config.proxyOptions() != null) {
            client = client.proxy(config.proxyOptions());
        }
        this.httpClient = client;
    }

    /**
     * 按配置数值装配内部连接池；透传口缺位时引擎自建的池走此装配。
     */
    private static ConnectionProvider buildConnectionProvider(ReactiveClientConfig config) {
        ConnectionProvider.Builder builder = ConnectionProvider.builder(RESOURCE_NAME)
                .maxConnections(config.maxConnections())
                .pendingAcquireMaxCount(config.pendingAcquireMaxCount())
                .pendingAcquireTimeout(config.pendingAcquireTimeout());
        if (config.maxIdleTime() != null) {
            builder.maxIdleTime(config.maxIdleTime());
        }
        if (config.maxLifeTime() != null) {
            builder.maxLifeTime(config.maxLifeTime());
        }
        return builder.build();
    }

    @Override
    public ByteResponseMessage request(HttpRequestMessage<byte[]> request) {
        Aggregation aggregation = new Aggregation();
        stream(request, aggregation);
        aggregation.awaitTerminal();
        if (aggregation.failure != null) {
            throw new ClientTransportException("聚合请求传输失败", aggregation.failure);
        }
        if (aggregation.statusLine == null) {
            throw new ClientTransportException("聚合请求传输提前终止，响应缺少状态行");
        }
        ByteResponseMessage response = new ByteResponseMessage(aggregation.statusLine, aggregation.aggregated());
        for (Map.Entry<String, List<String>> entry : aggregation.headers.asMap().entrySet()) {
            for (String value : entry.getValue()) {
                response.headers().add(entry.getKey(), value);
            }
        }
        return response;
    }

    @Override
    public StreamHandle stream(HttpRequestMessage<byte[]> request, StreamHandler handler) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        ensureOpen();

        String uri = request.requestLine().target().raw();
        if (!URI.create(uri).isAbsolute()) {
            throw new IllegalArgumentException("请求目标必须是绝对地址: " + uri);
        }

        byte[] body = request.content();
        boolean hasBody = body != null && body.length > 0;
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean terminal = new AtomicBoolean();

        Flux<ByteBuf> receive = httpClient
                .request(HttpMethod.valueOf(request.method().name()))
                .uri(uri)
                .send((req, outbound) -> hasBody
                        ? outbound.send(Mono.just(Unpooled.wrappedBuffer(body)))
                        : outbound.send(Mono.empty()))
                .responseConnection((response, connection) -> {
                    handler.onHeaders(statusLineOf(response), toHeaders(response.responseHeaders()));
                    return connection.inbound().receive();
                });

        Disposable disposable = receive.subscribe(
                buf -> {
                    try {
                        if (!cancelled.get()) {
                            byte[] chunk = new byte[buf.readableBytes()];
                            buf.readBytes(chunk);
                            handler.onData(chunk);
                        }
                    } finally {
                        ReferenceCountUtil.release(buf);
                    }
                },
                error -> {
                    if (terminal.compareAndSet(false, true)) {
                        handler.onThrowable(error);
                    }
                },
                () -> {
                    if (terminal.compareAndSet(false, true)) {
                        handler.onComplete();
                    }
                });

        return () -> {
            if (cancelled.compareAndSet(false, true)) {
                disposable.dispose();
                if (terminal.compareAndSet(false, true)) {
                    handler.onThrowable(new CancellationException("流式请求已取消"));
                }
            }
        };
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (ownConnectionProvider) {
                connectionProvider.disposeLater().block(CLOSE_TIMEOUT);
            }
            loopResources.disposeLater().block(CLOSE_TIMEOUT);
        }
    }

    /**
     * 确认引擎未关闭，关闭后请求入口一律拒绝。
     */
    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("引擎已关闭，不可再发起请求");
        }
    }

    /**
     * 由 netty 响应状态构造状态行：状态码与描述短语原样承接。
     */
    private static StatusLine statusLineOf(HttpClientResponse response) {
        HttpResponseStatus status = response.status();
        return StatusLine.of(status.code(), status.reasonPhrase());
    }

    /**
     * 由 netty 响应头实体构造头容器：条目逐条转入，同名多值按出现顺序保持。
     */
    private static Headers toHeaders(HttpHeaders nettyHeaders) {
        Headers headers = new Headers();
        for (Map.Entry<String, String> entry : nettyHeaders) {
            headers.add(entry.getKey(), entry.getValue());
        }
        return headers;
    }

    /**
     * 聚合请求的回调收集器：状态行、响应头与各分块在回调中累积，
     * 终态放行阻塞等待方；回调由传输侧串行递送，累积不加同步。
     */
    private static final class Aggregation implements StreamHandler {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private volatile StatusLine statusLine;
        private volatile Headers headers;
        private volatile Throwable failure;

        @Override
        public void onHeaders(StatusLine statusLine, Headers headers) {
            this.statusLine = statusLine;
            this.headers = new Headers(headers);
        }

        @Override
        public void onData(byte[] chunk) {
            buffer.writeBytes(chunk);
        }

        @Override
        public void onComplete() {
            latch.countDown();
        }

        @Override
        public void onThrowable(Throwable t) {
            this.failure = t;
            latch.countDown();
        }

        /**
         * 阻塞等待终态：完成或失败任一到达即放行。
         *
         * @throws ClientTransportException 等待期间被中断时抛出，中断状态先行恢复
         */
        void awaitTerminal() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ClientTransportException("等待聚合响应时被中断", e);
            }
        }

        /**
         * 汇总各分块为全量响应体。
         */
        byte[] aggregated() {
            return buffer.toByteArray();
        }
    }
}
