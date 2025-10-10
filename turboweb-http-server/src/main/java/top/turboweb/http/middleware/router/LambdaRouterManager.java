package top.turboweb.http.middleware.router;

import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.middleware.router.container.DefaultRouterContainer;
import top.turboweb.http.middleware.router.container.PathHelper;
import top.turboweb.http.middleware.router.container.RouterContainer;
import top.turboweb.http.middleware.router.info.ExactRouterInfo;
import top.turboweb.http.middleware.router.info.LambdaRouterDefinition;
import top.turboweb.http.middleware.router.info.TrieRouterInfo;

import java.util.List;

/**
 * 基于lambda的路由管理器
 */
public class LambdaRouterManager extends RouterManager {

    private final RouterContainer routerContainer = new DefaultRouterContainer();

    @Override
    protected RouterContainer getRouterContainer() {
        return this.routerContainer;
    }

    public LambdaRouterManager addGroup(LambdaRouterGroup group) {
        List<LambdaRouterGroup.RouterInfo> routers = group.getRouters();
        String prePath = group.requestPath();
        for (LambdaRouterGroup.RouterInfo router : routers) {
            String allPath = PathHelper.mergePath(prePath, router.getPath());
            // 判断是否有通配符
            if (allPath.contains("*")) {
                throw new TurboRouterDefinitionCreateException("方法路径不能包含通配符");
            }
            // 判断是否有路径参数
            int index = allPath.indexOf("{");
            if (index != -1) {
                TrieRouterInfo trieRouterInfo = routerContainer.getTrieRouterInfo();
                trieRouterInfo.addRouter(router.getMethod(), allPath, new LambdaRouterDefinition(router.getHandler()));
            } else {
                ExactRouterInfo exactRouterInfo = routerContainer.getExactRouterInfo();
                exactRouterInfo.addRouter(router.getMethod(), allPath, new LambdaRouterDefinition(router.getHandler()));
            }
        }
        return this;
    }

}
