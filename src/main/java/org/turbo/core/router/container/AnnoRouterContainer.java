package org.turbo.core.router.container;

import org.turbo.core.router.definition.RouterMethodDefinition;
import org.turbo.exception.TurboRouterRepeatException;

import java.util.Map;

/**
 * 注解路由容器
 */
public class AnnoRouterContainer extends RouterContainer {


    @Override
    public void addCompleteRouter(String method, String path, RouterMethodDefinition definition) {
        RouterInfo routerInfo = super.completeRouterInfo;
        this.checkRepeatAndSaveRouter(routerInfo, method, path, definition);
    }

    @Override
    public void addPathRouter(String method, String path, RouterMethodDefinition definition) {
        RouterInfo routerInfo = super.pathRouterInfo;
        this.checkRepeatAndSaveRouter(routerInfo, method, path, definition);
    }

    /**
     * 检查重复并且保存路由
     *
     * @param routerInfo 路由信息
     * @param method     请求方法
     * @param path       请求路径
     * @param definition 路由定义
     */
    private void checkRepeatAndSaveRouter(RouterInfo routerInfo, String method, String path, RouterMethodDefinition definition) {
        Map<String, RouterMethodDefinition> definitions = routerInfo.getDefinitionsByMethod(method);
        // 判断是否包含该路径
        if (definitions.containsKey(path)) {
            throw new TurboRouterRepeatException("路由重复: method:%s, path:%s".formatted(method, path));
        }
        definitions.put(path, definition);
    }
}
