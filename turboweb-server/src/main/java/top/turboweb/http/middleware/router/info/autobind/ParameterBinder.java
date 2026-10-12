package top.turboweb.http.middleware.router.info.autobind;

import top.turboweb.http.context.HttpContext;

/**
 * 自动注入的参数绑定器
 */
public interface ParameterBinder {

    /**
     * 解析参数
     * @param ctx 请求上下文
     * @return 参数
     */
    Object bindParameter(HttpContext ctx);
}
