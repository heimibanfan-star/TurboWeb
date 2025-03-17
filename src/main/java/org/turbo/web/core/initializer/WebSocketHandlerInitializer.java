package org.turbo.web.core.initializer;

import io.netty.bootstrap.ServerBootstrap;
import org.turbo.web.core.handler.piplines.WebSocketDispatcherHandler;
import org.turbo.web.core.http.ws.WebSocketHandler;

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
}
