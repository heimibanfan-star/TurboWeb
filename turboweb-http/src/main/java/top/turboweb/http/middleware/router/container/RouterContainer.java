package top.turboweb.http.middleware.router.container;

import top.turboweb.http.middleware.router.container.info.ExactRouterInfo;
import top.turboweb.http.middleware.router.container.info.RouterDefinition;
import top.turboweb.http.middleware.router.container.info.TrieRouterInfo;

import java.util.Map;

/**
 * 路由容器接口
 */
public interface RouterContainer {

    /**
     * 获取所有控制器实例
     *
     * @return 控制器实例
     */
    Map<Class<?>, Object> getControllerInstances();

    /**
     * 路由匹配结果
     */
    record TrieMatchResult(RouterDefinition definition, Map<String, String> params) {
    }

    /**
     * 获取精确匹配的路由定义
     *
     * @param method 请求方法
     * @param path   请求路径
     * @return 路由定义
     */
    RouterDefinition exactMatch(String method, String path);

    /**
     * 获取路径匹配的路由定义
     *
     * @param method 请求方法
     * @param path   请求路径
     * @return 路由定义
     */
    TrieMatchResult trieMatch(String method, String path);

    /**
     * 获取精确匹配的路由定义
     *
     * @return 路由定义
     */
    ExactRouterInfo getExactRouterInfo();

    /**
     * 获取路径匹配的路由定义
     *
     * @return 路由定义
     */
    TrieRouterInfo getTrieRouterInfo();
}
