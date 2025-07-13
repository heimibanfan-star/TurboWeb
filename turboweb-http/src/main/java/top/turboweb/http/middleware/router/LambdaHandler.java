package top.turboweb.http.middleware.router;

import top.turboweb.http.context.HttpContext;

/**
 * 基于lambda的控制器方法
 */
@FunctionalInterface
public interface LambdaHandler {

    Object handle(HttpContext context);
}
