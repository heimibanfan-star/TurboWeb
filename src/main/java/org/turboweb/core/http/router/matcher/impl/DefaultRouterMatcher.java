package org.turboweb.core.http.router.matcher.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.constants.MatchType;
import org.turboweb.core.http.router.container.RouterContainer;
import org.turboweb.core.http.router.definition.RouterMethodDefinition;
import org.turboweb.core.http.router.matcher.MatchResult;
import org.turboweb.core.http.router.matcher.RouterMatcher;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 默认路由匹配器
 */
public class DefaultRouterMatcher implements RouterMatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultRouterMatcher.class);
    private final RouterContainer routerContainer;

    public DefaultRouterMatcher(RouterContainer routerContainer) {
        this.routerContainer = routerContainer;
    }

    @Override
    public MatchResult match(String method, String path) {
        try {
            // 解析路径
            URI uri = URI.create(path);
            path = uri.getPath();
            // 去除末尾反斜杠
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            // 判断是否是空值
            if (path.isEmpty()) {
                path = "/";
            }
            // 查询精确路由匹配
            Map<String, RouterMethodDefinition> completeRouterDefinitions = routerContainer.getCompleteRouterDefinitions(method);
            RouterMethodDefinition definition = completeRouterDefinitions.get(path);
            if (definition != null) {
                return new MatchResult(definition, MatchType.COMPLETE);
            }
            return new MatchResult(mathPathRouter(method, path), MatchType.PATH);
        } catch (Exception e) {
            log.error("路由匹配失败", e);
            return null;
        }
    }

    @Override
    public Object getInstance(Class<?> clazz) {
        return routerContainer.getControllerInstances().get(clazz);
    }

    /**
     * 路径变量路由匹配
     *
     * @param method 请求方式
     * @param path 请求的url
     * @return 路由定义
     */
    private RouterMethodDefinition mathPathRouter(String method, String path) {
        Map<String, RouterMethodDefinition> pathRouterDefinitions = routerContainer.getPathRouterDefinitions(method);
        // 获取所有的路由定义
        Collection<RouterMethodDefinition> routerMethodDefinitions = pathRouterDefinitions.values();
        // 匹配路由
        for (RouterMethodDefinition definition : routerMethodDefinitions) {
            Pattern pattern = definition.getPattern();
            if (pattern == null) {
                continue;
            }
            if (pattern.matcher(path).matches()) {
                return definition;
            }
        }
        return null;
    }
}
