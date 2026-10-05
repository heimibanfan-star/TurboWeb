package top.turboweb.http.server.config;

import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.SslContext;

/**
 * 服务器配置类（不可变），承载与服务器相关的纯参数配置：连接限流参数（最大连接数、
 * io线程数、cpu核数）、ssl上下文与聚合器最大内容长度、http2开关与参数，
 * 由 {@link top.turboweb.http.server.manager.ApplicationManager} 在装配（configure）时消费，
 * 装配限流、ssl与协议编解码、http2等基础处理器；管理器自带默认配置，通过其
 * {@code config} 方法整体替换。
 * <p>
 * 与 {@link HttpConfig} 分工互补：应用级参数进应用建造者，服务器级参数进本配置；
 * 参数校验前置到 {@link Builder} 的链式方法中，非法参数立即抛出
 * {@link IllegalArgumentException}。
 *
 * <pre>{@code
 * ServerConfig config = ServerConfig.builder()
 *         .maxConnect(20000)
 *         .sslContext(sslContext)
 *         .enableHttp2(true)
 *         .build();
 * }</pre>
 */
public final class ServerConfig {

    /**
     * 最大连接数（连接限流处理器使用）
     */
    private final int maxConnect;

    /**
     * io工作线程数（连接限流处理器选择计数器实现时使用）
     */
    private final int ioThreadNum;

    /**
     * cpu核数（连接限流处理器选择计数器实现时使用）
     */
    private final int cpuNum;

    /**
     * ssl上下文，为null表示不启用ssl（启用http2时必传）
     */
    private final SslContext sslContext;

    /**
     * http聚合器最大内容长度
     */
    private final int maxContentLength;

    /**
     * 是否启用http2，启用后ssl为前置要求（h2依赖ALPN协议协商）
     */
    private final boolean enableHttp2;

    /**
     * http2的设置
     */
    private final Http2Settings http2Settings;

    private ServerConfig(Builder builder) {
        this.maxConnect = builder.maxConnect;
        this.ioThreadNum = builder.ioThreadNum;
        this.cpuNum = builder.cpuNum;
        this.sslContext = builder.sslContext;
        this.maxContentLength = builder.maxContentLength;
        this.enableHttp2 = builder.enableHttp2;
        this.http2Settings = builder.http2Settings;
    }

    /**
     * 创建配置构建器，各字段初始为默认值
     */
    public static Builder builder() {
        return new Builder();
    }

    public int getMaxConnect() {
        return maxConnect;
    }

    public int getIoThreadNum() {
        return ioThreadNum;
    }

    public int getCpuNum() {
        return cpuNum;
    }

    /**
     * 获取ssl上下文，可能为null（未启用SSL）
     */
    public SslContext getSslContext() {
        return sslContext;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public boolean isEnableHttp2() {
        return enableHttp2;
    }

    public Http2Settings getHttp2Settings() {
        return http2Settings;
    }

    /**
     * {@link ServerConfig} 的建造者，链式方法名与配置字段同名，非法参数立即抛出
     * {@link IllegalArgumentException}。各字段默认值：连接数10000、io线程数cpu核数*2、
     * cpu核数取运行时可用处理器数、不启用ssl、聚合器10MB、不启用http2、
     * http2参数为并发流256/初始窗口1MB/帧16KB/头列表8KB。
     */
    public static final class Builder {

        private int maxConnect = 10000;

        private int ioThreadNum = Runtime.getRuntime().availableProcessors() * 2;

        private int cpuNum = Runtime.getRuntime().availableProcessors();

        private SslContext sslContext = null;

        private int maxContentLength = 1024 * 1024 * 10;

        private boolean enableHttp2 = false;

        private Http2Settings http2Settings = new Http2Settings()
                .maxConcurrentStreams(256)
                .initialWindowSize(1 << 20)
                .maxFrameSize(1 << 14)
                .maxHeaderListSize(8192);

        private Builder() {
        }

        /**
         * 设置最大连接数，必须大于0
         */
        public Builder maxConnect(int maxConnect) {
            if (maxConnect <= 0) {
                throw new IllegalArgumentException("maxConnect must be greater than 0");
            }
            this.maxConnect = maxConnect;
            return this;
        }

        /**
         * 设置io工作线程数，必须大于0
         */
        public Builder ioThreadNum(int ioThreadNum) {
            if (ioThreadNum <= 0) {
                throw new IllegalArgumentException("ioThreadNum must be greater than 0");
            }
            this.ioThreadNum = ioThreadNum;
            return this;
        }

        /**
         * 设置cpu核数，必须大于0
         */
        public Builder cpuNum(int cpuNum) {
            if (cpuNum <= 0) {
                throw new IllegalArgumentException("cpuNum must be greater than 0");
            }
            this.cpuNum = cpuNum;
            return this;
        }

        /**
         * 设置ssl上下文，传null表示不启用ssl；启用http2时本参数必传（h2依赖ALPN协商）
         */
        public Builder sslContext(SslContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * 设置http聚合器最大内容长度，必须大于等于1024
         */
        public Builder maxContentLength(int maxContentLength) {
            if (maxContentLength < 1024) {
                throw new IllegalArgumentException("maxContentLength必须大于等于1024");
            }
            this.maxContentLength = maxContentLength;
            return this;
        }

        /**
         * 设置是否启用http2，true时ssl为前置要求
         */
        public Builder enableHttp2(boolean enableHttp2) {
            this.enableHttp2 = enableHttp2;
            return this;
        }

        /**
         * 设置http2的参数，不能为null
         */
        public Builder http2Settings(Http2Settings http2Settings) {
            if (http2Settings == null) {
                throw new IllegalArgumentException("http2Settings can not be null");
            }
            this.http2Settings = http2Settings;
            return this;
        }

        /**
         * 构建不可变的配置对象
         */
        public ServerConfig build() {
            return new ServerConfig(this);
        }
    }
}
