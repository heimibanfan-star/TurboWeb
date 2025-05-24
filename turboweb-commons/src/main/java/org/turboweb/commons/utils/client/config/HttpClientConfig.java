package org.turboweb.commons.utils.client.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * http客户端的配置
 */
public class HttpClientConfig {

    // 最大连接数，设置连接池中的最大连接数。
    private int maxConnections = Integer.MAX_VALUE;
    // 请求连接时的最大等待时间, 单位ms
    private long pendingAcquireTimeout = 30 * 1000;
    // 连接的最大空闲时间, 单位ms
    private long maxIdleTime = 5 * 60 * 1000;
    // 连接最大生命周期, 单位ms
    private long maxLifeTime = 30 * 60 * 1000;
    // 获取连接的最大请求数
    private int pendingAcquireMaxCount = Integer.MAX_VALUE;
    // 设置空闲连接自动清理
    private long evictInBackground = 5 * 60 * 1000;
    // 设置字符编码
    private Charset charset = StandardCharsets.UTF_8;

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public long getPendingAcquireTimeout() {
        return pendingAcquireTimeout;
    }

    public void setPendingAcquireTimeout(long pendingAcquireTimeout) {
        this.pendingAcquireTimeout = pendingAcquireTimeout;
    }

    public long getMaxIdleTime() {
        return maxIdleTime;
    }

    public void setMaxIdleTime(long maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    public long getMaxLifeTime() {
        return maxLifeTime;
    }

    public void setMaxLifeTime(long maxLifeTime) {
        this.maxLifeTime = maxLifeTime;
    }

    public int getPendingAcquireMaxCount() {
        return pendingAcquireMaxCount;
    }

    public void setPendingAcquireMaxCount(int pendingAcquireMaxCount) {
        this.pendingAcquireMaxCount = pendingAcquireMaxCount;
    }

    public long getEvictInBackground() {
        return evictInBackground;
    }

    public void setEvictInBackground(long evictInBackground) {
        this.evictInBackground = evictInBackground;
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }
}
