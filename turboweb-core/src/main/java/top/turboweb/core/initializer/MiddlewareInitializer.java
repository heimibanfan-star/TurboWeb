package top.turboweb.core.initializer;

import top.turboweb.core.config.ServerParamConfig;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.session.SessionManagerProxy;

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
