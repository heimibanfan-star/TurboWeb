package top.turboweb.http.router;

import top.turboweb.http.context.HttpContext;

/**
 * 路由处理器管理器接口，具体的功能有其不同的实现来解决
 */
public interface RouteHandlerManager {

    /**
     * 处理请求
     * @param context 请求上下文
     * @return 请求处理的结果
     */
    Object handle(HttpContext context);
}
