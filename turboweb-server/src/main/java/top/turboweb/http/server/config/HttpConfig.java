package top.turboweb.http.server.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * http请求应用配置类（不可变），只包含与http请求应用相关的纯参数配置：
 * 请求串行化开关、请求日志开关、缺省字符集、session回收参数、http调度器限流参数与磁盘IO线程池参数。
 * <p>
 * 协议编解码、线程、连接数等服务器层配置见 {@link ServerConfig}；
 * 中间件、session管理器等组件对象不属于配置，由 HttpRequestApplication 的建造者持有。
 * <p>
 * 通过建造者模式构建，参数校验前置到 {@link Builder} 的链式方法中，非法参数立即抛出异常。
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

    /**
     * 磁盘IO线程池缓冲队列大小
     */
    private final int diskOpeThreadCacheQueue;

    /**
     * 磁盘IO线程池核心队列大小
     */
    private final int diskOpeThreadCoreQueue;

    /**
     * 磁盘IO线程池最大线程数
     */
    private final int diskOpeThreadMaxThreadNum;

    /**
     * 缺省字符集：请求体解码在请求头未声明 charset 时回落到它；
     * URL 路径参数解码与响应文本、文件名、Cookie 值的编码恒取它，不受请求头声明影响
     */
    private final Charset defaultCharset;

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
        this.diskOpeThreadCacheQueue = builder.diskOpeThreadCacheQueue;
        this.diskOpeThreadCoreQueue = builder.diskOpeThreadCoreQueue;
        this.diskOpeThreadMaxThreadNum = builder.diskOpeThreadMaxThreadNum;
        this.defaultCharset = builder.defaultCharset;
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

    public int getDiskOpeThreadCacheQueue() {
        return diskOpeThreadCacheQueue;
    }

    public int getDiskOpeThreadCoreQueue() {
        return diskOpeThreadCoreQueue;
    }

    public int getDiskOpeThreadMaxThreadNum() {
        return diskOpeThreadMaxThreadNum;
    }

    public Charset getDefaultCharset() {
        return defaultCharset;
    }

    /**
     * {@link HttpConfig} 的建造者，链式方法名与配置字段同名，
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

        private int diskOpeThreadCacheQueue = 4096;

        private int diskOpeThreadCoreQueue = 6;

        private int diskOpeThreadMaxThreadNum = Runtime.getRuntime().availableProcessors() * 2;

        private Charset defaultCharset = StandardCharsets.UTF_8;

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
                throw new IllegalArgumentException("sessionCheckTime must be >= 1000");
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
                throw new IllegalArgumentException("sessionCheckThreshold must be > 0");
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
                throw new IllegalArgumentException("httpSchedulerLimitCount must be >= 0");
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
                throw new IllegalArgumentException("httpSchedulerLimitCacheThread must be >= 0");
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
                throw new IllegalArgumentException("httpSchedulerLimitTimeout must be >= 0");
            }
            this.httpSchedulerLimitTimeout = httpSchedulerLimitTimeout;
            return this;
        }

        /**
         * 磁盘IO线程池缓冲队列大小，装配http调度器时用于创建弹性线程池
         *
         * @param diskOpeThreadCacheQueue 缓冲队列容量，必须大于0
         */
        public Builder diskOpeThreadCacheQueue(int diskOpeThreadCacheQueue) {
            if (diskOpeThreadCacheQueue < 1) {
                throw new IllegalArgumentException("diskOpeThreadCacheQueue must be > 0");
            }
            this.diskOpeThreadCacheQueue = diskOpeThreadCacheQueue;
            return this;
        }

        /**
         * 磁盘IO线程池核心队列大小，装配http调度器时用于创建弹性线程池
         *
         * @param diskOpeThreadCoreQueue 核心队列容量，必须大于0
         */
        public Builder diskOpeThreadCoreQueue(int diskOpeThreadCoreQueue) {
            if (diskOpeThreadCoreQueue < 1) {
                throw new IllegalArgumentException("diskOpeThreadCoreQueue must be > 0");
            }
            this.diskOpeThreadCoreQueue = diskOpeThreadCoreQueue;
            return this;
        }

        /**
         * 磁盘IO线程池最大线程数，装配http调度器时用于创建弹性线程池
         *
         * @param diskOpeThreadMaxThreadNum 最大线程数，必须大于0
         */
        public Builder diskOpeThreadMaxThreadNum(int diskOpeThreadMaxThreadNum) {
            if (diskOpeThreadMaxThreadNum < 1) {
                throw new IllegalArgumentException("diskOpeThreadMaxThreadNum must be > 0");
            }
            this.diskOpeThreadMaxThreadNum = diskOpeThreadMaxThreadNum;
            return this;
        }

        /**
         * 缺省字符集，默认UTF-8。请求体解码在请求头Content-Type未声明charset时回落到它；
         * URL路径参数解码与响应文本、文件名、Cookie值的编码恒取它，不受请求头声明影响
         *
         * @param defaultCharset 字符集，为null立即抛出 {@link NullPointerException}
         */
        public Builder defaultCharset(Charset defaultCharset) {
            this.defaultCharset = Objects.requireNonNull(defaultCharset, "defaultCharset can not be null");
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
