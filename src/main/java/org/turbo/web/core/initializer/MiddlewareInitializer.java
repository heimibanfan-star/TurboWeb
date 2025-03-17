package org.turbo.web.core.initializer;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.session.SessionManagerProxy;

/**
 * 中间件的初始化器
 */
public interface MiddlewareInitializer {

    /**
     * 添加控制器对象
     *
     * @param controllers 控制器对象
     */
    void addController(Object... controllers);

    /**
     * 添加中间件对象
     *
     * @param middleware 中间件对象
     */
    void addMiddleware(Middleware... middleware);

    /**
     * 初始化
     *
     * @return 中间件的头结点
     */
    Middleware init(
        SessionManagerProxy sessionManagerProxy,
        Class<?> mainClass,
        ExceptionHandlerMatcher matcher,
        ServerParamConfig config
    );
}
