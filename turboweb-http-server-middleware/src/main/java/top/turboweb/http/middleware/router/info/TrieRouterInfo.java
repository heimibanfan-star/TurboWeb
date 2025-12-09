package top.turboweb.http.middleware.router.info;

import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.commons.struct.trie.RestUrlTrie;

import java.util.Map;
import java.util.Optional;

/**
 * 模板路由信息
 */
public class TrieRouterInfo {

    private final Map<String, RestUrlTrie<RouterDefinition>> routerStrategy;

    {
        routerStrategy = Map.of(
            "GET", new RestUrlTrie<>(),
            "POST", new RestUrlTrie<>(),
            "PUT", new RestUrlTrie<>(),
            "DELETE", new RestUrlTrie<>(),
            "PATCH", new RestUrlTrie<>()
        );
    }

    /**
     * 添加路由
     *
     * @param method       请求方法
     * @param path         路由路径
     * @param routerDefinition 路由定义
     */
    public void addRouter(String method, String path, RouterDefinition routerDefinition) {
        method = Optional.ofNullable(method).orElse("");
        // 获取路由定义
        RestUrlTrie<RouterDefinition> pathTrie = routerStrategy.get(method.toUpperCase());
        if (pathTrie == null) {
            throw new RuntimeException("不支持的方法:" + method);
        }
        // 尝试添加路由
        try {
            pathTrie.insert(path, routerDefinition);
        } catch (Exception e) {
            throw new TurboRouterException(e, TurboRouterException.ROUTER_REPEAT);
        }
    }

    /**
     * 获取路由定义
     *
     * @param method 请求方法
     * @return 路由定义
     */
    public RestUrlTrie<RouterDefinition> getPathTrie(String method) {
        RestUrlTrie<RouterDefinition> routerDefinitionPathTrie = routerStrategy.get(method.toUpperCase());
        if (routerDefinitionPathTrie == null) {
            throw new TurboRouterException("不支持的方法:" + method, TurboRouterException.ROUTER_NOT_MATCH);
        }
        return routerDefinitionPathTrie;
    }
}
