package org.turboweb.core.http.router.container;

import org.turboweb.core.http.router.definition.RouterMethodDefinition;
import org.turboweb.commons.exception.TurboMethodNotSupportException;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由的容器
 */
public abstract class RouterContainer {

    protected static class RouterInfo {
        private final Map<String, RouterMethodDefinition> getDefinitions = new HashMap<>();
        private final Map<String, RouterMethodDefinition> postDefinitions = new HashMap<>();
        private final Map<String, RouterMethodDefinition> putDefinitions = new HashMap<>();
        private final Map<String, RouterMethodDefinition> deleteDefinitions = new HashMap<>();
        private final Map<String, RouterMethodDefinition> patchDefinitions = new HashMap<>();

        private final Map<String, Map<String, RouterMethodDefinition>> definitionStrategy = new HashMap<>();

        {
            definitionStrategy.put("GET", getDefinitions);
            definitionStrategy.put("POST", postDefinitions);
            definitionStrategy.put("PUT", putDefinitions);
            definitionStrategy.put("DELETE", deleteDefinitions);
            definitionStrategy.put("PATCH", patchDefinitions);
        }

        /**
         * 根据方法获取对应的路由定义
         *
         * @param method 方法
         * @return 路由定义
         */
        public Map<String, RouterMethodDefinition> getDefinitionsByMethod(String method) {
            if (!definitionStrategy.containsKey(method)) {
                throw new TurboMethodNotSupportException("不支持的方法:" + method);
            }
            return definitionStrategy.get(method);
        }
    }

    /**
     * 存储精确路由的容器
     */
    protected final RouterInfo completeRouterInfo = new RouterInfo();

    /**
     * 存储路径参数路由的容器
     */
    protected final RouterInfo pathRouterInfo = new RouterInfo();

    /**
     * 存储控制器实例
     */
    protected final Map<Class<?>, Object> controllerInstances = new HashMap<>();

    /**
     * 添加精确路由
     *
     * @param method     方法
     * @param path       路径
     * @param definition 路由定义
     */
    public abstract void addCompleteRouter(String method, String path, RouterMethodDefinition definition);

    /**
     * 添加路径参数路由
     *
     * @param method     方法
     * @param path       路径
     * @param definition 路由定义
     */
    public abstract void addPathRouter(String method, String path, RouterMethodDefinition definition);

    /**
     * 获取控制器实例
     *
     * @return 控制器实例
     */
    public Map<Class<?>, Object> getControllerInstances() {
        return controllerInstances;
    }

    /**
     * 获取精确路由定义
     *
     * @param method 方法
     * @return 路由定义
     */
    public Map<String, RouterMethodDefinition> getCompleteRouterDefinitions(String method) {
        return completeRouterInfo.getDefinitionsByMethod(method);
    }

    /**
     * 获取路径参数路由定义
     *
     * @param method 方法
     * @return 路由定义
     */
    public Map<String, RouterMethodDefinition> getPathRouterDefinitions(String method) {
        return pathRouterInfo.getDefinitionsByMethod(method);
    }
}
