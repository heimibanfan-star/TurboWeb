package top.turboweb.http.middleware.router.container;

import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.struct.trie.PathTrie;
import top.turboweb.http.middleware.router.container.info.ExactRouterInfo;
import top.turboweb.http.middleware.router.container.info.RouterDefinition;
import top.turboweb.http.middleware.router.container.info.TrieRouterInfo;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 注解是路由的路由容器
 */
public class AnnoRouterContainer implements RouterContainer {

    private final ExactRouterInfo exactRouterInfo;
    private final TrieRouterInfo trieRouterInfo;
    private final Map<Class<?>, Object> controllerInstances;

    public AnnoRouterContainer() {
        this.exactRouterInfo = new ExactRouterInfo();
        this.trieRouterInfo = new TrieRouterInfo();
        this.controllerInstances = new HashMap<>();
    }

    @Override
    public Map<Class<?>, Object> getControllerInstances() {
        return this.controllerInstances;
    }

    @Override
    public RouterDefinition exactMatch(String method, String path) {
        // 根据请求方式获取路由定义
        Map<String, RouterDefinition> routerDefinition = exactRouterInfo.getRouterDefinition(method);
        // 去除路径的查询参数部分
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return routerDefinition.get(path);
    }

    @Override
    public TrieMatchResult trieMatch(String method, String path) {
        // 根据请求方式获取对应的前缀树
        PathTrie<RouterDefinition> pathTrie = trieRouterInfo.getPathTrie(method);
        // 去除路径的查询参数部分
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }
        // 进行前缀树的匹配
        Optional<PathTrie.MatchResult<RouterDefinition>> optional = pathTrie.paramMatch(path);
        return optional
                .map(routerDefinitionMatchResult -> {
                    Map<String, String> params = routerDefinitionMatchResult.getParams();
                    Map<String, String> newParams = new HashMap<>(params.size());
                    params.forEach((key, value) -> {
                        value = decode(value);
                        newParams.put(key, value);
                    });
                    return new TrieMatchResult(routerDefinitionMatchResult.getValue(), newParams);
                })
                .orElse(null);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, GlobalConfig.getRequestCharset());
    }

    public ExactRouterInfo getExactRouterInfo() {
        return exactRouterInfo;
    }

    @Override
    public TrieRouterInfo getTrieRouterInfo() {
        return trieRouterInfo;
    }
}
