package org.turboweb.core.initializer;

import org.turboweb.websocket.dispatch.WebSocketDispatcherHandler;
import org.turboweb.websocket.WebSocketHandler;

/**
 * websocket处理器的初始化器
 */
public interface WebSocketHandlerInitializer {

    /**
     * 设置websocket处理器
     *
     * @param path               路径
     * @param webSocketHandler   处理器
     */
    void setWebSocketHandler(String path, WebSocketHandler webSocketHandler);

    /**
     * 初始化
     *
     * @return websocket处理器
     */
    WebSocketDispatcherHandler init();

    /**
     * 是否使用
     *
     * @return 是否使用
     */
    boolean isUse();

    /**
     * 获取路径
     *
     * @return 路径
     */
    String getPath();

    /**
     * 设置forkJoin线程数
     *
     * @param threadNum 线程数
     */
    void setForkJoinThreadNum(int threadNum);
}
