package top.turboweb.core.initializer;

import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.router.RouterManager;

/**
 * 中间件的初始化器
 */
public interface MiddlewareInitializer {

    /**
     * 设置路由管理器
     *
     * @param routerManager 路由管理器
     */
    void routerManager(RouterManager routerManager);

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
    );
}
