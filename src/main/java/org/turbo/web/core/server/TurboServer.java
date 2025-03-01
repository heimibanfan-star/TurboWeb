package org.turbo.web.core.server;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.session.SessionManager;
import org.turbo.web.core.init.TurboServerInit;

/**
 * 服务器接口
 */
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
    void addController(Class<?>... controllers);

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
    void addExceptionHandler(Class<?>... exceptionHandler);

    /**
     * 添加初始化器
     *
     * @param turboServerInits 初始化器
     */
    void addTurboServerInit(TurboServerInit... turboServerInits);

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
}
