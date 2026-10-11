package top.turboweb.http.router;

import java.lang.reflect.Method;

/**
 * 路由处理器的元信息
 * @param url 标准的url，以/开头，结尾不包含/
 * @param instance 处理器的实例
 * @param routeClass 路由所属的类
 * @param method 方法
 */
public record RouteHandlerMetaInfo(
        String url,
        Object instance,
        Class<?> routeClass,
        Method method
) {
}
