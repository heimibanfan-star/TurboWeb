package top.turboweb.core.config;

/**
 * 服务器初始化配置类
 */
public class HttpServerConfig {

    /**
     * 最大请求内容长度
     */
    private int maxContentLength = 1024 * 1024 * 10;

    /**
     * 是否显示请求日志
     */
    private boolean showRequestLog = true;

    /**
     * session检查时间间隔
     */
    private long sessionCheckTime = 300000;

    /**
     * session最大不活跃时间
     */
    private long sessionMaxNotUseTime = -1;

    /**
     * 触发session检查的阈值
     */
    private long sessionCheckThreshold = 256;

    /**
     * 备用线程池缓存队列大小
     */
    private int diskOpeThreadCacheQueue = 4096;

    /**
     * 调度器是否开启限流
     */
    private boolean enableHttpSchedulerLimit = false;

    /**
     * 调度器限流数量
     */
    private int httpSchedulerLimitCount = 10000;

    /**
     * 可以进入等待的线程数量
     */
    private int httpSchedulerLimitCacheThread = 2000;

    /**
     * 线程池限流超时时间
     */
    private long httpSchedulerLimitTimeout = 500;

    /**
     * 备用线程池核心队列大小
     */
    private int diskOpeThreadCoreQueue = 6;

    /**
     * cpu核数
     */
    private int cpuNum = Runtime.getRuntime().availableProcessors();

    /**
     * 最大连接数
     */
    private int maxConnections = 10000;

    /**
     * 备用线程池最大线程数
     */
    private int diskOpeThreadMaxThreadNum = Runtime.getRuntime().availableProcessors() * 2;

    public long getSessionCheckThreshold() {
        return sessionCheckThreshold;
    }

    /**
     * 检查session的间隔时间，单位毫秒
     */
    public void setSessionCheckThreshold(long sessionCheckThreshold) {
        if (sessionCheckThreshold < 1) {
            throw new IllegalArgumentException("sessionCheckThreshold必须大于0");
        }
        this.sessionCheckThreshold = sessionCheckThreshold;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    /**
     * 最大请求体长度
     */
    public void setMaxContentLength(int maxContentLength) {
        if (maxContentLength < 1024) {
            throw new IllegalArgumentException("maxContentLength必须大于等于1024");
        }
        this.maxContentLength = maxContentLength;
    }


    public boolean isShowRequestLog() {
        return showRequestLog;
    }

    /**
     * 是否显示请求日志
     *
     * @param showRequestLog true显示
     */
    public void setShowRequestLog(boolean showRequestLog) {
        this.showRequestLog = showRequestLog;
    }

    public long getSessionCheckTime() {
        return sessionCheckTime;
    }

    /**
     * 设置session检查间隔
     *
     * @param sessionCheckTime session检查间隔，单位ms
     */
    public void setSessionCheckTime(long sessionCheckTime) {
        if (sessionCheckTime < 1000) {
            throw new IllegalArgumentException("sessionCheckTime必须大于1000");
        }
        this.sessionCheckTime = sessionCheckTime;
    }

    public long getSessionMaxNotUseTime() {
        return sessionMaxNotUseTime;
    }

    /**
     * 设置session最大不活跃时间
     *
     * @param sessionMaxNotUseTime, 单位ms
     */
    public void setSessionMaxNotUseTime(long sessionMaxNotUseTime) {
        this.sessionMaxNotUseTime = sessionMaxNotUseTime;
    }

    public int getDiskOpeThreadCacheQueue() {
        return diskOpeThreadCacheQueue;
    }

    /**
     * 设置备用线程池重缓冲队列的大小
     *
     * @param diskOpeThreadCacheQueue 缓冲队列大小
     */
    public void setDiskOpeThreadCacheQueue(int diskOpeThreadCacheQueue) {
        if (diskOpeThreadCacheQueue < 32) {
            throw new IllegalArgumentException("缓冲队列大小不能小于32");
        }
        this.diskOpeThreadCacheQueue = diskOpeThreadCacheQueue;
    }

    public int getDiskOpeThreadCoreQueue() {
        return diskOpeThreadCoreQueue;
    }

    /**
     * 设置备用线程池核心队列大小
     *
     * @param diskOpeThreadCoreQueue 核心队列大小
     */
    public void setDiskOpeThreadCoreQueue(int diskOpeThreadCoreQueue) {
        if (diskOpeThreadCoreQueue < 1) {
            throw new IllegalArgumentException("核心队列大小不能小于1");
        }
        this.diskOpeThreadCoreQueue = diskOpeThreadCoreQueue;
    }

    public int getDiskOpeThreadMaxThreadNum() {
        return diskOpeThreadMaxThreadNum;
    }

    /**
     * 设置备用线程池最大线程数
     * @param diskOpeThreadMaxThreadNum 最大线程数
     */
    public void setDiskOpeThreadMaxThreadNum(int diskOpeThreadMaxThreadNum) {
        if (diskOpeThreadMaxThreadNum < 1) {
            throw new IllegalArgumentException("最大线程数不能小于1");
        }
        this.diskOpeThreadMaxThreadNum = diskOpeThreadMaxThreadNum;
    }

    public boolean isEnableHttpSchedulerLimit() {
        return enableHttpSchedulerLimit;
    }

    public void setEnableHttpSchedulerLimit(boolean enableHttpSchedulerLimit) {
        this.enableHttpSchedulerLimit = enableHttpSchedulerLimit;
    }

    public int getHttpSchedulerLimitCount() {
        return httpSchedulerLimitCount;
    }

    public void setHttpSchedulerLimitCount(int httpSchedulerLimitCount) {
        if (httpSchedulerLimitCount < 0) {
            throw new IllegalArgumentException("httpSchedulerLimitCacheThread must be greater than or equal to 0");
        }
        this.httpSchedulerLimitCount = httpSchedulerLimitCount;
    }

    public int getHttpSchedulerLimitCacheThread() {
        return httpSchedulerLimitCacheThread;
    }

    public void setHttpSchedulerLimitCacheThread(int httpSchedulerLimitCacheThread) {
        if (httpSchedulerLimitCacheThread < 0) {
            throw new IllegalArgumentException("httpSchedulerLimitCacheThread must be greater than or equal to 0");
        }
        this.httpSchedulerLimitCacheThread = httpSchedulerLimitCacheThread;
    }

    public long getHttpSchedulerLimitTimeout() {
        return httpSchedulerLimitTimeout;
    }

    public void setHttpSchedulerLimitTimeout(long httpSchedulerLimitTimeout) {
        if (httpSchedulerLimitTimeout < 0) {
            throw new IllegalArgumentException("httpSchedulerLimitTimeout must be greater than or equal to 0");
        }
        this.httpSchedulerLimitTimeout = httpSchedulerLimitTimeout;
    }

    public int getCpuNum() {
        return cpuNum;
    }

    public void setCpuNum(int cpuNum) {
        if (cpuNum < 1) {
            throw new IllegalArgumentException("cpuNum must be greater than or equal to 1");
        }
        this.cpuNum = cpuNum;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        if (maxConnections < 1) {
            throw new IllegalArgumentException("maxConnections must be greater than or equal to 1");
        }
        this.maxConnections = maxConnections;
    }


}
