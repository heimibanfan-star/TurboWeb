package org.turbo.web.utils.client;

import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.config.HttpClientConfig;
import org.turbo.web.core.http.client.PromiseHttpClient;
import org.turbo.web.core.http.client.ReactiveHttpClient;
import org.turbo.web.exception.TurboHttpClientException;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * http客户端工具
 */
public class HttpClientUtils {

    private static final Logger log = LoggerFactory.getLogger(HttpClientUtils.class);
    private static HttpClient httpClient;
    private static boolean init = false;
    private static EventLoopGroup executors;

    // 返回promise的客户端
    private static volatile PromiseHttpClient promiseHttpClient;
    // 返回反应式流的客户端
    private static volatile ReactiveHttpClient reactiveHttpClient;

    /**
     * 对http客户端进行初始化
     *
     * @param config 初始化的配置
     */
    public static void initClient(HttpClientConfig config, EventLoopGroup group) {
        if (init) {
            return;
        }
        synchronized (HttpClientUtils.class) {
            if (init) {
                return;
            }
            executors = group;
            ConnectionProvider provider = ConnectionProvider.builder("httpClient")
                    .maxConnections(config.getMaxConnections())
                    .pendingAcquireTimeout(Duration.ofMillis(config.getPendingAcquireTimeout()))
                    .maxIdleTime(Duration.ofMillis(config.getMaxIdleTime()))
                    .maxLifeTime(Duration.ofMillis(config.getMaxLifeTime()))
                    .pendingAcquireMaxCount(config.getPendingAcquireMaxCount())
                    .evictInBackground(Duration.ofMillis(config.getEvictInBackground()))
                    .build();
            httpClient = HttpClient.create(provider).runOn(group);
            init = true;
        }
    }

    /**
     * 获取http客户端
     *
     * @return http客户端
     */
    public static HttpClient httpClient() {
        if (!init) {
            throw new TurboHttpClientException("HttpClientUtils未初始化");
        }
        return httpClient;
    }

    /**
     * 获取promise客户端
     *
     * @return promise客户端
     */
    public static PromiseHttpClient promiseHttpClient() {
        if (!init) {
            throw new TurboHttpClientException("HttpClientUtils未初始化");
        }
        if (promiseHttpClient != null) {
            return promiseHttpClient;
        }
        synchronized (PromiseHttpClient.class) {
            if (promiseHttpClient != null) {
                return promiseHttpClient;
            }
            promiseHttpClient = new PromiseHttpClient(httpClient, executors);
            return promiseHttpClient;
        }
    }

    /**
     * 获取响应式流客户端
     *
     * @return 反应式流客户端
     */
    public static ReactiveHttpClient reactiveHttpClient() {
        if (!init) {
            throw new TurboHttpClientException("HttpClientUtils未初始化");
        }
        if (reactiveHttpClient != null) {
            return reactiveHttpClient;
        }
        synchronized (ReactiveHttpClient.class) {
            if (reactiveHttpClient != null) {
                return reactiveHttpClient;
            }
            reactiveHttpClient = new ReactiveHttpClient(httpClient);
            return reactiveHttpClient;
       }
    }
}
