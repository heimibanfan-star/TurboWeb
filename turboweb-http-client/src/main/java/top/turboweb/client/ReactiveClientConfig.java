package top.turboweb.client;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.SslProvider;
import reactor.netty.transport.ProxyProvider;

/**
 * 响应式客户端引擎的不可变传输配置：连接池数值参数、超时、事件循环数量与行为开关，
 * 另留连接池、TLS 与代理三处 reactor-netty 原生配置的透传口——
 * 透传者以透传为准，对应数值参数不再生效；未透传者由引擎按数值参数内部装配。
 *
 * <p>经 {@link #builder()} 装配、{@link Builder#build()} 一次性产出：
 * 字段全部 final、构建后不可变，{@link #build()} 可多次调用，产物互不影响。</p>
 */
public class ReactiveClientConfig {

    private static final int DEFAULT_MAX_CONNECTIONS = 500;
    private static final Duration DEFAULT_PENDING_ACQUIRE_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_EVENT_LOOP_COUNT = 1;
    /** 未显式配置排队数量时，取最大连接数的该倍数（对齐 reactor-netty 缺省口径） */
    private static final int DEFAULT_PENDING_ACQUIRE_MULTIPLIER = 2;

    private final ConnectionProvider connectionProvider;
    private final SslProvider sslProvider;
    private final Consumer<ProxyProvider.TypeSpec> proxyOptions;
    private final int maxConnections;
    private final int pendingAcquireMaxCount;
    private final Duration pendingAcquireTimeout;
    private final Duration maxIdleTime;
    private final Duration maxLifeTime;
    private final Duration connectTimeout;
    private final Duration responseTimeout;
    private final int eventLoopCount;
    private final boolean compression;
    private final boolean followRedirect;
    private final boolean wiretap;

    /**
     * 由建造者装配产物，配置对象对外不提供公有构造入口。
     *
     * @param builder 已收集完属性值的建造者
     */
    private ReactiveClientConfig(Builder builder) {
        if (builder.maxConnections < 1) {
            throw new IllegalArgumentException("maxConnections must be positive");
        }
        this.connectionProvider = builder.connectionProvider;
        this.sslProvider = builder.sslProvider;
        this.proxyOptions = builder.proxyOptions;
        this.maxConnections = builder.maxConnections;
        this.pendingAcquireMaxCount = builder.pendingAcquireMaxCount != null
                ? builder.pendingAcquireMaxCount
                : builder.maxConnections * DEFAULT_PENDING_ACQUIRE_MULTIPLIER;
        this.pendingAcquireTimeout = requirePositive(builder.pendingAcquireTimeout, "pendingAcquireTimeout");
        this.maxIdleTime = requirePositiveOrNull(builder.maxIdleTime, "maxIdleTime");
        this.maxLifeTime = requirePositiveOrNull(builder.maxLifeTime, "maxLifeTime");
        this.connectTimeout = requirePositive(builder.connectTimeout, "connectTimeout");
        this.responseTimeout = requirePositiveOrNull(builder.responseTimeout, "responseTimeout");
        if (builder.eventLoopCount < 1) {
            throw new IllegalArgumentException("eventLoopCount must be positive");
        }
        this.eventLoopCount = builder.eventLoopCount;
        this.compression = builder.compression;
        this.followRedirect = builder.followRedirect;
        this.wiretap = builder.wiretap;
    }

    /**
     * 创建建造者，各属性从默认值起步。
     *
     * @return 新建造者
     */
    public static Builder builder() {
        return new Builder();
    }

    /** 透传的连接池；未透传时为 {@code null}，由引擎按数值参数装配。 */
    public ConnectionProvider connectionProvider() {
        return connectionProvider;
    }

    /** 透传的 TLS 配置；未透传时为 {@code null}。 */
    public SslProvider sslProvider() {
        return sslProvider;
    }

    /** 透传的代理配置回调；未透传时为 {@code null}。 */
    public Consumer<ProxyProvider.TypeSpec> proxyOptions() {
        return proxyOptions;
    }

    /** 连接池最大连接数。 */
    public int maxConnections() {
        return maxConnections;
    }

    /** 连接池排队获取的最大等待数。 */
    public int pendingAcquireMaxCount() {
        return pendingAcquireMaxCount;
    }

    /** 连接池排队获取的超时。 */
    public Duration pendingAcquireTimeout() {
        return pendingAcquireTimeout;
    }

    /** 连接空闲上限，超时回收；{@code null} 表示不按空闲回收。 */
    public Duration maxIdleTime() {
        return maxIdleTime;
    }

    /** 连接存活上限，超时回收；{@code null} 表示不按存活回收。 */
    public Duration maxLifeTime() {
        return maxLifeTime;
    }

    /** 建立连接的超时。 */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /** 响应接收的超时；{@code null} 表示不设响应超时。 */
    public Duration responseTimeout() {
        return responseTimeout;
    }

    /** 引擎自建事件循环组的循环数量。 */
    public int eventLoopCount() {
        return eventLoopCount;
    }

    /** 是否对响应启用解压。 */
    public boolean compression() {
        return compression;
    }

    /** 是否自动跟随重定向。 */
    public boolean followRedirect() {
        return followRedirect;
    }

    /** 是否开启网络事件级日志。 */
    public boolean wiretap() {
        return wiretap;
    }

    /**
     * {@link ReactiveClientConfig} 的建造者：每个属性一个流式配置方法，
     * 重复配置以最后一次为准，未配置者取默认值。
     */
    public static class Builder {

        private ConnectionProvider connectionProvider;
        private SslProvider sslProvider;
        private Consumer<ProxyProvider.TypeSpec> proxyOptions;
        private int maxConnections = DEFAULT_MAX_CONNECTIONS;
        private Integer pendingAcquireMaxCount;
        private Duration pendingAcquireTimeout = DEFAULT_PENDING_ACQUIRE_TIMEOUT;
        private Duration maxIdleTime;
        private Duration maxLifeTime;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration responseTimeout = DEFAULT_RESPONSE_TIMEOUT;
        private int eventLoopCount = DEFAULT_EVENT_LOOP_COUNT;
        private boolean compression;
        private boolean followRedirect;
        private boolean wiretap;

        /** 透传连接池，透传后最大连接数等池数值参数不再生效。 */
        public Builder connectionProvider(ConnectionProvider connectionProvider) {
            this.connectionProvider = connectionProvider;
            return this;
        }

        /** 透传 TLS 配置。 */
        public Builder sslProvider(SslProvider sslProvider) {
            this.sslProvider = sslProvider;
            return this;
        }

        /** 透传代理配置回调，回调语义见 reactor-netty 的 proxy(Consumer)。 */
        public Builder proxyOptions(Consumer<ProxyProvider.TypeSpec> proxyOptions) {
            this.proxyOptions = proxyOptions;
            return this;
        }

        /** 设置连接池最大连接数。 */
        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        /** 设置连接池排队获取的最大等待数；缺省取最大连接数的两倍。 */
        public Builder pendingAcquireMaxCount(int pendingAcquireMaxCount) {
            this.pendingAcquireMaxCount = pendingAcquireMaxCount;
            return this;
        }

        /** 设置连接池排队获取的超时。 */
        public Builder pendingAcquireTimeout(Duration pendingAcquireTimeout) {
            this.pendingAcquireTimeout = pendingAcquireTimeout;
            return this;
        }

        /** 设置连接空闲上限，{@code null} 表示不按空闲回收。 */
        public Builder maxIdleTime(Duration maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
            return this;
        }

        /** 设置连接存活上限，{@code null} 表示不按存活回收。 */
        public Builder maxLifeTime(Duration maxLifeTime) {
            this.maxLifeTime = maxLifeTime;
            return this;
        }

        /** 设置建立连接的超时。 */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /** 设置响应接收的超时，{@code null} 表示不设响应超时。 */
        public Builder responseTimeout(Duration responseTimeout) {
            this.responseTimeout = responseTimeout;
            return this;
        }

        /** 设置引擎自建事件循环组的循环数量。 */
        public Builder eventLoopCount(int eventLoopCount) {
            this.eventLoopCount = eventLoopCount;
            return this;
        }

        /** 设置是否对响应启用解压。 */
        public Builder compression(boolean compression) {
            this.compression = compression;
            return this;
        }

        /** 设置是否自动跟随重定向。 */
        public Builder followRedirect(boolean followRedirect) {
            this.followRedirect = followRedirect;
            return this;
        }

        /** 设置是否开启网络事件级日志。 */
        public Builder wiretap(boolean wiretap) {
            this.wiretap = wiretap;
            return this;
        }

        /**
         * 产出不可变配置对象。
         *
         * @return 以当前收集值装配的新配置
         */
        public ReactiveClientConfig build() {
            return new ReactiveClientConfig(this);
        }
    }

    /**
     * 校验必填时长：非空且为正，否则抛出对应异常。
     */
    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        return requirePositiveOrNull(value, name);
    }

    /**
     * 校验可选时长：为空放行，非空时必须为正。
     */
    private static Duration requirePositiveOrNull(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
