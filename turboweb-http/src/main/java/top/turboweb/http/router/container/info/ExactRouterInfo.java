package top.turboweb.http.router.container.info;

import top.turboweb.commons.exception.TurboMethodNotSupportException;
import top.turboweb.commons.exception.TurboRouterException;

import java.util.*;

/**
 * 精确的路由信息
 */
public class ExactRouterInfo {

    private final Map<String, Map<String, RouterDefinition>> routerStrategy;

    {
        routerStrategy = Map.of(
                "GET", new HashMap<>(),
                "POST", new HashMap<>(),
                "PUT", new HashMap<>(),
                "DELETE", new HashMap<>(),
                "PATCH", new HashMap<>()
        );
    }

    /**
     * 获取指定方法的路由信息
     * @param method 请求方法
     * @return 路由信息
     */
    public Map<String, RouterDefinition> getRouterDefinition(String method) {
        method = Optional.ofNullable(method).orElse("").toUpperCase();
        Map<String, RouterDefinition> stringRouterDefinitionMap = routerStrategy.get(method);
        if (Objects.isNull(stringRouterDefinitionMap)) {
            throw new TurboMethodNotSupportException("不支持的方法:" + method);
        }
        return stringRouterDefinitionMap;
    }

    /**
     * 添加路由信息
     * @param method 请求方法
     * @param path 路由路径
     * @param routerDefinition 路由信息
     */
    public void addRouter(String method, String path, RouterDefinition routerDefinition) {
        method = Optional.ofNullable(method).orElse("").toUpperCase();
        Map<String, RouterDefinition> stringRouterDefinitionMap = routerStrategy.get(method);
        if (Objects.isNull(stringRouterDefinitionMap)) {
            throw new TurboMethodNotSupportException("不支持的方法:" + method);
        }
        // 判断路径是否存在
        if (stringRouterDefinitionMap.containsKey(path)) {
            throw new TurboRouterException("路由重复: method:%s, path:%s".formatted(method, path), TurboRouterException.ROUTER_REPEAT);
        }
        stringRouterDefinitionMap.put(path, routerDefinition);
    }
}
