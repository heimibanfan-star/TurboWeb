package top.turboweb.http.middleware.router;

import io.netty.handler.codec.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户存储lambda风格路由的路由组
 */
public abstract class LambdaRouterGroup {

    private final RouterRegister routerRegister = new RouterRegister();

    public static class RouterInfo {
        private final String method;
        private final String path;
        private final LambdaHandler handler;

        public RouterInfo(String method, String path, LambdaHandler handler) {
            this.method = method;
            this.path = path;
            this.handler = handler;
        }

        public String getPath() {
            return path;
        }

        public String getMethod() {
            return method;
        }

        public LambdaHandler getHandler() {
            return handler;
        }
    }

    /**
     * 存储路由信息
     */
    public static class RouterRegister {
        List<RouterInfo> routers = new ArrayList<>();

        public RouterRegister get(String path, LambdaHandler handler) {
            routers.add(new RouterInfo(HttpMethod.GET.name(), path, handler));
            return this;
        }

        public RouterRegister post(String path, LambdaHandler handler) {
            routers.add(new RouterInfo(HttpMethod.POST.name(), path, handler));
            return this;
        }

        public RouterRegister put(String path, LambdaHandler handler) {
            routers.add(new RouterInfo(HttpMethod.PUT.name(), path, handler));
            return this;
        }

        public RouterRegister patch(String path, LambdaHandler handler) {
            routers.add(new RouterInfo(HttpMethod.PATCH.name(), path, handler));
            return this;
        }

        public RouterRegister delete(String path, LambdaHandler handler) {
            routers.add(new RouterInfo(HttpMethod.DELETE.name(), path, handler));
            return this;
        }
    }

    /**
     * 获取请求路径
     * @return 请求路径
     */
    public abstract String requestPath();

    /**
     * 注册路由
     * @param register 路由注册器
     */
    protected abstract void registerRoute(RouterRegister register);

    /**
     * 获取路由信息
     * @return 路由信息
     */
    public List<RouterInfo> getRouters() {
        this.registerRoute(this.routerRegister);
        return this.routerRegister.routers;
    }

}
