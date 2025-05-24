package org.turboweb.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储websocket的连接信息
 */
public class WebSocketConnectInfoContainer {

    private static final Map<String, WebSocketConnectInfo> webSocketConnectInfoMap = new ConcurrentHashMap<>(1024);

    public static WebSocketConnectInfo getWebSocketConnectInfo(String key) {
        return webSocketConnectInfoMap.get(key);
    }

    public static void putWebSocketConnectInfo(String key, WebSocketConnectInfo webSocketConnectInfo) {
        webSocketConnectInfoMap.put(key, webSocketConnectInfo);
    }

    public static void removeWebSocketConnectInfo(String key) {
        webSocketConnectInfoMap.remove(key);
    }
}
