package org.turbo.web.utils.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.config.HttpClientConfig;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * http客户端工具
 */
public class HttpClientUtils {

    private static final Logger log = LoggerFactory.getLogger(HttpClientUtils.class);
    private static HttpClient httpClient;
    private static boolean init = false;

    /**
     * 对http客户端进行初始化
     *
     * @param config 初始化的配置
     */
    public static void initClient(HttpClientConfig config) {
        if (init) {
            return;
        }
        synchronized (HttpClientUtils.class) {
            if (init) {
                return;
            }
            ConnectionProvider provider = ConnectionProvider.builder("httpClient")
                    .maxConnections(config.getMaxConnections())
                    .pendingAcquireTimeout(Duration.ofMillis(config.getPendingAcquireTimeout()))
                    .maxIdleTime(Duration.ofMillis(config.getMaxIdleTime()))
                    .maxLifeTime(Duration.ofMillis(config.getMaxLifeTime()))
                    .pendingAcquireMaxCount(config.getPendingAcquireMaxCount())
                    .evictInBackground(Duration.ofMillis(config.getEvictInBackground()))
                    .build();
            httpClient = HttpClient.create(provider);
            init = true;
            log.info("HttpClientUtils初始化成功");
        }
    }
}
