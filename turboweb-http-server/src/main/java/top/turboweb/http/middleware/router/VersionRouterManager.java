package top.turboweb.http.middleware.router;

import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.router.container.RouterContainer;

import java.util.*;

/**
 * 版本控制路由管理器
 */
public abstract class VersionRouterManager extends RouterManager{

    protected final static class Managers {
        private final Map<String, RouterManager> routers = new HashMap<>();

        private void addRouterManager(String version, RouterManager routerManager) {
            // 禁止添加版本控制路由管理器
            if (routerManager instanceof VersionRouterManager) {
                throw new IllegalArgumentException("Cannot add a VersionRouterManager to a VersionRouterManager");
            }
            // 判断版本是否重复
            if (routers.containsKey(version)) {
                throw new TurboRouterException("The version of RouterManager is repeated: " + version, TurboRouterException.ROUTER_REPEAT);
            }
            // 添加路由管理器
            routers.put(version, routerManager);
        }

        public RouterManager getRouterManager(String version) {
            return routers.get(version);
        }

        public Set<String> getVersions() {
            return new HashSet<>(routers.keySet());
        }
    }

    private final Managers managers = new Managers();


    public void addRouterManager(String version, RouterManager routerManager) {
        managers.addRouterManager(version, routerManager);
    }

    @Override
    public final Object invoke(HttpContext ctx) {
        RouterManager routerManager = getRouterManager(ctx, managers);
        if (routerManager == null) {
            throw new TurboRouterException("The version of RouterManager is not found: " + ctx.query("version"), TurboRouterException.ROUTER_NOT_MATCH);
        }
        return routerManager.invoke(ctx);
    }

    /**
     * 获取路由管理器
     * @param context 请求上下文
     * @param managers 路由管理器集合
     * @return 路由管理器
     */
    protected abstract RouterManager getRouterManager(HttpContext context, Managers managers);

    @Override
    protected final RouterContainer getRouterContainer() {
        return null;
    }
}
