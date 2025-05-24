package org.turboweb.core.server.legacy;

import org.turboweb.core.config.ServerParamConfig;
import org.turboweb.core.gateway.Gateway;
import org.turboweb.core.http.middleware.Middleware;
import org.turboweb.core.http.session.SessionManager;
import org.turboweb.websocket.WebSocketHandler;
import org.turboweb.core.initializer.HttpClientInitializer;
import org.turboweb.core.listener.TurboWebListener;

/**
 * 服务器接口
 */
@Deprecated
public interface TurboServer {

    /**
     * 启动服务器
     */
    void start();

    /**
     * 启动服务器
     *
     * @param port 端口
     */
    void start(int port);

    /**
     * 添加控制器
     *
     * @param controllers 控制器
     */
    void addController(Object... controllers);

    /**
     * 设置配置
     *
     * @param config 配置
     */
    void setConfig(ServerParamConfig config);

    /**
     * 添加中间件
     *
     * @param middleware 中间件
     */
    void addMiddleware(Middleware... middleware);

    /**
     * 添加异常处理器
     *
     * @param exceptionHandler 异常处理器
     */
    void addExceptionHandler(Object... exceptionHandler);

    /**
     * 添加初始化器
     *
     * @param turboWebListeners 初始化器
     */
    void addTurboServerInit(TurboWebListener... turboWebListeners);

    /**
     * 默认初始化
     *
     * @param flag 是否默认初始化
     */
    void doDefaultTurboInit(boolean flag);

    /**
     * 设置SessionManager
     *
     * @param sessionManager SessionManager
     */
    void setSessionManager(SessionManager sessionManager);

    /**
     * 设置是否为响应式服务器
     *
     * @param flag 是否为响应式服务器
     */
    void setIsReactiveServer(boolean flag);

    /**
     * 增加websocket处理器
     *
     * @param path websocket处理的路径
     * @param webSocketHandler 处理器
     */
    void setWebSocketHandler(String path, WebSocketHandler webSocketHandler);

    /**
     * 获取http客户端的初始化器
     * http客户端的初始化器
     */
    HttpClientInitializer httpClient();

    /**
     * 设置网关
     * @param gateway 网关
     */
    void setGateway(Gateway gateway);
}
