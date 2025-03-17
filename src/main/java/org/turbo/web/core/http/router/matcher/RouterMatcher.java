package org.turbo.web.core.http.router.matcher;

/**
 * 路由匹配器
 */
public interface RouterMatcher {

    /**
     * 路由匹配
     *
     * @param method 请求方法
     * @param path   请求路径
     * @return 路由方法定义
     */
    MatchResult match(String method, String path);

    /**
     * 获取实例
     *
     * @param clazz 字节码对象
     * @return 实例对象
     */
    Object getInstance(Class<?> clazz);
}
