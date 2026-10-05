package top.turboweb.http.server.config;

/**
 * http请求应用配置类（不可变），只包含与http请求应用相关的纯参数配置：
 * 请求串行化开关、请求日志开关、session回收参数与http调度器限流参数。
 * <p>
 * 协议编解码、线程、连接数等服务器层配置见 {@link HttpServerConfig}；
 * 中间件、session管理器等组件对象不属于配置，由 HttpRequestApplication 的建造者持有。
 * <p>
 * 通过建造者模式构建，字段与默认值对齐 top.turboweb.http.server.config.HttpServerConfig 中
 * 应用相关字段（例外：{@code serializePerConnection} 在应用层默认关闭，需显式开启），
 * 参数校验前置到 {@link Builder} 的链式方法中，非法参数立即抛出异常。
 *
 * <pre>{@code
 * HttpConfig config = HttpConfig.builder()
 *         .serializePerConnection(true)
 *         .showRequestLog(true)
 *         .sessionMaxNotUseTime(1800000)
 *         .build();
 * }</pre>
 */
public final class HttpConfig {

    /**
     * 是否每个连接都进串行化处理，默认关闭
     */
    private final boolean serializePerConnection;

    /**
     * 是否显示请求日志
     */
    private final boolean showRequestLog;

    /**
     * session检查时间间隔
     */
    private final long sessionCheckTime;

    /**
     * session最大不活跃时间
     */
    private final long sessionMaxNotUseTime;

    /**
     * 触发session检查的阈值
     */
    private final long sessionCheckThreshold;

    /**
     * 调度器是否开启限流
     */
    private final boolean enableHttpSchedulerLimit;

    /**
     * 调度器限流数量
     */
    private final int httpSchedulerLimitCount;

    /**
     * 可以进入等待的线程数量
     */
    private final int httpSchedulerLimitCacheThread;

    /**
     * 线程池限流超时时间
     */
    private final long httpSchedulerLimitTimeout;

    private HttpConfig(Builder builder) {
        this.serializePerConnection = builder.serializePerConnection;
        this.showRequestLog = builder.showRequestLog;
        this.sessionCheckTime = builder.sessionCheckTime;
        this.sessionMaxNotUseTime = builder.sessionMaxNotUseTime;
        this.sessionCheckThreshold = builder.sessionCheckThreshold;
        this.enableHttpSchedulerLimit = builder.enableHttpSchedulerLimit;
        this.httpSchedulerLimitCount = builder.httpSchedulerLimitCount;
        this.httpSchedulerLimitCacheThread = builder.httpSchedulerLimitCacheThread;
        this.httpSchedulerLimitTimeout = builder.httpSchedulerLimitTimeout;
    }

    /**
     * 创建配置构建器，各字段初始为默认值
     */
    public static Builder builder() {
        return new Builder();
    }

    public boolean isSerializePerConnection() {
        return serializePerConnection;
    }

    public boolean isShowRequestLog() {
        return showRequestLog;
    }

    public long getSessionCheckTime() {
        return sessionCheckTime;
    }

    public long getSessionMaxNotUseTime() {
        return sessionMaxNotUseTime;
    }

    public long getSessionCheckThreshold() {
        return sessionCheckThreshold;
    }

    public boolean isEnableHttpSchedulerLimit() {
        return enableHttpSchedulerLimit;
    }

    public int getHttpSchedulerLimitCount() {
        return httpSchedulerLimitCount;
    }

    public int getHttpSchedulerLimitCacheThread() {
        return httpSchedulerLimitCacheThread;
    }

    public long getHttpSchedulerLimitTimeout() {
        return httpSchedulerLimitTimeout;
    }

    /**
     * {@link HttpConfig} 的建造者，链式方法名与配置字段同名，
     * 默认值与校验逻辑与 {@code HttpServerConfig.Builder} 的应用相关字段保持一致，
     * 非法参数立即抛出 {@link IllegalArgumentException}。
     */
    public static final class Builder {

        private boolean serializePerConnection = false;

        private boolean showRequestLog = true;

        private long sessionCheckTime = 300000;

        private long sessionMaxNotUseTime = -1;

        private long sessionCheckThreshold = 256;

        private boolean enableHttpSchedulerLimit = false;

        private int httpSchedulerLimitCount = 10000;

        private int httpSchedulerLimitCacheThread = 2000;

        private long httpSchedulerLimitTimeout = 500;

        private Builder() {
        }

        /**
         * 是否每个连接都进行串行化处理，默认关闭，需显式开启
         *
         * @param serializePerConnection true串行化
         */
        public Builder serializePerConnection(boolean serializePerConnection) {
            this.serializePerConnection = serializePerConnection;
            return this;
        }

        /**
         * 是否显示请求日志
         *
         * @param showRequestLog true显示
         */
        public Builder showRequestLog(boolean showRequestLog) {
            this.showRequestLog = showRequestLog;
            return this;
        }

        /**
         * 设置session检查间隔
         *
         * @param sessionCheckTime session检查间隔，单位ms，必须大于等于1000
         */
        public Builder sessionCheckTime(long sessionCheckTime) {
            if (sessionCheckTime < 1000) {
                throw new IllegalArgumentException("sessionCheckTime必须大于1000");
            }
            this.sessionCheckTime = sessionCheckTime;
            return this;
        }

        /**
         * 设置session最大不活跃时间
         *
         * @param sessionMaxNotUseTime session最大不活跃时间，单位ms
         */
        public Builder sessionMaxNotUseTime(long sessionMaxNotUseTime) {
            this.sessionMaxNotUseTime = sessionMaxNotUseTime;
            return this;
        }

        /**
         * 设置触发session检查的阈值
         *
         * @param sessionCheckThreshold 阈值，必须大于0
         */
        public Builder sessionCheckThreshold(long sessionCheckThreshold) {
            if (sessionCheckThreshold < 1) {
                throw new IllegalArgumentException("sessionCheckThreshold必须大于0");
            }
            this.sessionCheckThreshold = sessionCheckThreshold;
            return this;
        }

        /**
         * 调度器是否开启限流
         *
         * @param enableHttpSchedulerLimit true开启
         */
        public Builder enableHttpSchedulerLimit(boolean enableHttpSchedulerLimit) {
            this.enableHttpSchedulerLimit = enableHttpSchedulerLimit;
            return this;
        }

        /**
         * 调度器限流数量
         *
         * @param httpSchedulerLimitCount 限流数量，必须大于等于0
         */
        public Builder httpSchedulerLimitCount(int httpSchedulerLimitCount) {
            if (httpSchedulerLimitCount < 0) {
                throw new IllegalArgumentException("httpSchedulerLimitCount must be greater than or equal to 0");
            }
            this.httpSchedulerLimitCount = httpSchedulerLimitCount;
            return this;
        }

        /**
         * 可以进入等待的线程数量
         *
         * @param httpSchedulerLimitCacheThread 线程数量，必须大于等于0
         */
        public Builder httpSchedulerLimitCacheThread(int httpSchedulerLimitCacheThread) {
            if (httpSchedulerLimitCacheThread < 0) {
                throw new IllegalArgumentException("httpSchedulerLimitCacheThread must be greater than or equal to 0");
            }
            this.httpSchedulerLimitCacheThread = httpSchedulerLimitCacheThread;
            return this;
        }

        /**
         * 线程池限流超时时间
         *
         * @param httpSchedulerLimitTimeout 超时时间，单位ms，必须大于等于0
         */
        public Builder httpSchedulerLimitTimeout(long httpSchedulerLimitTimeout) {
            if (httpSchedulerLimitTimeout < 0) {
                throw new IllegalArgumentException("httpSchedulerLimitTimeout must be greater than or equal to 0");
            }
            this.httpSchedulerLimitTimeout = httpSchedulerLimitTimeout;
            return this;
        }

        /**
         * 构建不可变的配置对象
         */
        public HttpConfig build() {
            return new HttpConfig(this);
        }
    }
}
