package org.turbo.web.core.initializer.impl;

import io.netty.bootstrap.ServerBootstrap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.handler.piplines.WebSocketDispatcherHandler;
import org.turbo.web.core.http.ws.WebSocketHandler;
import org.turbo.web.core.initializer.WebSocketHandlerInitializer;
import org.turbo.web.exception.TurboWebSocketException;

/**
 * 默认的websocket初始化器
 */
public class DefaultWebSocketHandlerInitializer implements WebSocketHandlerInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultWebSocketHandlerInitializer.class);
    // 是否使用websocket
    private boolean useWebSocket = false;
    // websocket的处理器
    private WebSocketHandler webSocketHandler;
    // websocket处理的路径
    private String websocketPath;

    @Override
    public void setWebSocketHandler(String path, WebSocketHandler webSocketHandler) {
        useWebSocket = true;
        this.websocketPath = path;
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public WebSocketDispatcherHandler init() {
        return initWebSocketDispatcherHandler();
    }

    @Override
    public boolean isUse() {
        return this.useWebSocket;
    }

    @Override
    public String getPath() {
        return websocketPath;
    }

    /**
     * 初始化websocket处理器
     *
     * @return websocket处理器
     */
    private WebSocketDispatcherHandler initWebSocketDispatcherHandler() {
        if (webSocketHandler == null) {
            throw new TurboWebSocketException("websocket处理器不能为空");
        }
        if (StringUtils.isBlank(websocketPath)) {
            throw new TurboWebSocketException("websocket路径不能为空");
        }
        WebSocketDispatcherHandler webSocketDispatcherHandler = new WebSocketDispatcherHandler(webSocketHandler);
        log.info("websocket处理器初始化成功");
        return webSocketDispatcherHandler;
    }
}
