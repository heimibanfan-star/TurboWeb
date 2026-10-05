package top.turboweb.http.server.config;

import io.netty.handler.codec.http2.Http2Settings;

/**
 * 服务器初始化配置类（不可变）。
 * <p>
 * 通过建造者模式构建，字段与默认值对齐 top.turboweb.core.config.HttpServerConfig，
 * 参数校验前置到 {@link Builder} 的链式方法中，非法参数立即抛出异常。
 *
 * <pre>{@code
 * HttpServerConfig config = HttpServerConfig.builder()
 *         .maxContentLength(1024 * 1024)
 *         .showRequestLog(false)
 *         .build();
 * }</pre>
 */
public final class HttpServerConfig {

    /**
     * 最大请求内容长度
     */
    private final int maxContentLength;

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
     * 备用线程池缓存队列大小
     */
    private final int diskOpeThreadCacheQueue;

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

    /**
     * 备用线程池核心队列大小
     */
    private final int diskOpeThreadCoreQueue;

    /**
     * cpu核数
     */
    private final int cpuNum;

    /**
     * 最大连接数
     */
    private final int maxConnections;

    /**
     * 备用线程池最大线程数
     */
    private final int diskOpeThreadMaxThreadNum;

    /**
     * 是否每个连接都进串行化处理
     */
    private final boolean serializePerConnection;

    /**
     * http2的设置
     */
    private final Http2Settings http2Settings;

    private HttpServerConfig(Builder builder) {
        this.maxContentLength = builder.maxContentLength;
        this.showRequestLog = builder.showRequestLog;
        this.sessionCheckTime = builder.sessionCheckTime;
        this.sessionMaxNotUseTime = builder.sessionMaxNotUseTime;
        this.sessionCheckThreshold = builder.sessionCheckThreshold;
        this.diskOpeThreadCacheQueue = builder.diskOpeThreadCacheQueue;
        this.enableHttpSchedulerLimit = builder.enableHttpSchedulerLimit;
        this.httpSchedulerLimitCount = builder.httpSchedulerLimitCount;
        this.httpSchedulerLimitCacheThread = builder.httpSchedulerLimitCacheThread;
        this.httpSchedulerLimitTimeout = builder.httpSchedulerLimitTimeout;
        this.diskOpeThreadCoreQueue = builder.diskOpeThreadCoreQueue;
        this.cpuNum = builder.cpuNum;
        this.maxConnections = builder.maxConnections;
        this.diskOpeThreadMaxThreadNum = builder.diskOpeThreadMaxThreadNum;
        this.serializePerConnection = builder.serializePerConnection;
        this.http2Settings = builder.http2Settings;
    }

    /**
     * 创建配置构建器，各字段初始为默认值
     */
    public static Builder builder() {
        return new Builder();
    }

    public int getMaxContentLength() {
        return maxContentLength;
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

    public int getDiskOpeThreadCacheQueue() {
        return diskOpeThreadCacheQueue;
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

    public int getDiskOpeThreadCoreQueue() {
        return diskOpeThreadCoreQueue;
    }

    public int getCpuNum() {
        return cpuNum;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getDiskOpeThreadMaxThreadNum() {
        return diskOpeThreadMaxThreadNum;
    }

    public boolean isSerializePerConnection() {
        return serializePerConnection;
    }

    public Http2Settings getHttp2Settings() {
        return http2Settings;
    }

    /**
     * {@link HttpServerConfig} 的建造者，链式方法名与配置字段同名，
     * 校验逻辑与原 setter 保持一致，非法参数立即抛出 {@link IllegalArgumentException}。
     */
    public static final class Builder {

        private int maxContentLength = 1024 * 1024 * 10;

        private boolean showRequestLog = true;

        private long sessionCheckTime = 300000;

        private long sessionMaxNotUseTime = -1;

        private long sessionCheckThreshold = 256;

        private int diskOpeThreadCacheQueue = 4096;

        private boolean enableHttpSchedulerLimit = false;

        private int httpSchedulerLimitCount = 10000;

        private int httpSchedulerLimitCacheThread = 2000;

        private long httpSchedulerLimitTimeout = 500;

        private int diskOpeThreadCoreQueue = 6;

        private int cpuNum = Runtime.getRuntime().availableProcessors();

        private int maxConnections = 10000;

        private int diskOpeThreadMaxThreadNum = Runtime.getRuntime().availableProcessors() * 2;

        private boolean serializePerConnection = true;

        private Http2Settings http2Settings = new Http2Settings()
                .maxConcurrentStreams(256)
                .initialWindowSize(1 << 20)
                .maxFrameSize(1 << 14)
                .maxHeaderListSize(8192);

        private Builder() {
        }

        /**
         * 最大请求体长度
         *
         * @param maxContentLength 最大请求体长度，必须大于等于1024
         */
        public Builder maxContentLength(int maxContentLength) {
            if (maxContentLength < 1024) {
                throw new IllegalArgumentException("maxContentLength必须大于等于1024");
            }
            this.maxContentLength = maxContentLength;
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
         * 设置备用线程池缓冲队列的大小
         *
         * @param diskOpeThreadCacheQueue 缓冲队列大小，不能小于32
         */
        public Builder diskOpeThreadCacheQueue(int diskOpeThreadCacheQueue) {
            if (diskOpeThreadCacheQueue < 32) {
                throw new IllegalArgumentException("缓冲队列大小不能小于32");
            }
            this.diskOpeThreadCacheQueue = diskOpeThreadCacheQueue;
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
         * 设置备用线程池核心队列大小
         *
         * @param diskOpeThreadCoreQueue 核心队列大小，不能小于1
         */
        public Builder diskOpeThreadCoreQueue(int diskOpeThreadCoreQueue) {
            if (diskOpeThreadCoreQueue < 1) {
                throw new IllegalArgumentException("核心队列大小不能小于1");
            }
            this.diskOpeThreadCoreQueue = diskOpeThreadCoreQueue;
            return this;
        }

        /**
         * cpu核数
         *
         * @param cpuNum cpu核数，必须大于等于1
         */
        public Builder cpuNum(int cpuNum) {
            if (cpuNum < 1) {
                throw new IllegalArgumentException("cpuNum must be greater than or equal to 1");
            }
            this.cpuNum = cpuNum;
            return this;
        }

        /**
         * 最大连接数
         *
         * @param maxConnections 最大连接数，必须大于等于1
         */
        public Builder maxConnections(int maxConnections) {
            if (maxConnections < 1) {
                throw new IllegalArgumentException("maxConnections must be greater than or equal to 1");
            }
            this.maxConnections = maxConnections;
            return this;
        }

        /**
         * 设置备用线程池最大线程数
         *
         * @param diskOpeThreadMaxThreadNum 最大线程数，不能小于1
         */
        public Builder diskOpeThreadMaxThreadNum(int diskOpeThreadMaxThreadNum) {
            if (diskOpeThreadMaxThreadNum < 1) {
                throw new IllegalArgumentException("最大线程数不能小于1");
            }
            this.diskOpeThreadMaxThreadNum = diskOpeThreadMaxThreadNum;
            return this;
        }

        /**
         * 是否每个连接都进行串行化处理
         *
         * @param serializePerConnection true串行化
         */
        public Builder serializePerConnection(boolean serializePerConnection) {
            this.serializePerConnection = serializePerConnection;
            return this;
        }

        /**
         * 设置http2的参数
         *
         * @param http2Settings http2的设置，不能为null
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
        public HttpServerConfig build() {
            return new HttpServerConfig(this);
        }
    }
}
