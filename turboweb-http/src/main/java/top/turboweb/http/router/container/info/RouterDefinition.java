package top.turboweb.http.router.container.info;

import top.turboweb.http.context.HttpContext;


/**
 * 路由定义信息
 */
public interface RouterDefinition {

    /**
     * 调用路由处理器
     *
     * @param ctx 请求上下文
     * @return 方法返回值
     */
    Object invoke(HttpContext ctx) throws Throwable;
}