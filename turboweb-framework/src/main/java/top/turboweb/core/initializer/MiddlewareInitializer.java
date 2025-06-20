package top.turboweb.core.initializer;

import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.session.SessionManagerHolder;

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
     * 添加控制器对象
     *
     * @param instance 控制器对象
     * @param originClass 原始控制器类
     */
    void addController(Object instance, Class<?> originClass);

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
        SessionManagerHolder sessionManagerHolder,
        Class<?> mainClass,
        ExceptionHandlerMatcher matcher,
        HttpServerConfig config
    );
}
