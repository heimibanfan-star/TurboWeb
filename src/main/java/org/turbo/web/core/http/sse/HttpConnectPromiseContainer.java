package org.turbo.web.core.http.sse;

import io.netty.util.concurrent.Promise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储HTTP连接事件的容器
 */
public class HttpConnectPromiseContainer {

    private static final Map<String, Promise<Boolean>> CONTAINER = new ConcurrentHashMap<>(1024);

    public static void put(String channelId, Promise<Boolean> promise) {
        CONTAINER.put(channelId, promise);
    }

    public static Promise<Boolean> get(String channelId) {
        return CONTAINER.get(channelId);
    }

    public static void remove(String channelId) {
        CONTAINER.remove(channelId);
    }
}
