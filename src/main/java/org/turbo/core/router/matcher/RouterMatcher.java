package org.turbo.core.router.matcher;

import org.turbo.core.router.definition.RouterMethodDefinition;

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
    RouterMethodDefinition match(String method, String path);
}
