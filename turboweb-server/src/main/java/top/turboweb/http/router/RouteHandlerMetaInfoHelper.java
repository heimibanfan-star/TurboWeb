package top.turboweb.http.router;

import io.netty.handler.codec.http.HttpMethod;
import top.turboweb.rest.annotation.router.Delete;
import top.turboweb.rest.annotation.router.Get;
import top.turboweb.rest.annotation.router.Patch;
import top.turboweb.rest.annotation.router.Post;
import top.turboweb.rest.annotation.router.Put;
import top.turboweb.rest.annotation.router.Route;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 路由处理器元信息解析助手
 */
public class RouteHandlerMetaInfoHelper {

    private RouteHandlerMetaInfoHelper() {
    }

    /**
     * 解析路由类上标注了请求方式注解的公有方法,生成处理器元信息列表
     *
     * @param baseUrl 基础路径,null 按空串处理
     * @param routeInstance 路由处理器实例,不能为 null
     * @param targetClass 路由类,扫描以此类为准;为 null 时取 routeInstance 的类
     * @return 元信息列表,按 url、方法名、参数表升序
     * @throws NullPointerException     routeInstance 为 null
     * @throws IllegalArgumentException 某方法同时标注多个请求方式注解;
     *                                  或某段路径含连续的 /,属非法路径;或三级路径都提不出段,
     *                                  合并退化成不受支持的根路径
     */
    public static List<RouteHandlerMetaInfo> parse(String baseUrl, Object routeInstance, Class<?> targetClass) {
        Objects.requireNonNull(routeInstance, "The route handler instance must not be null");
        Class<?> routeClass = targetClass != null ? targetClass : routeInstance.getClass();
        String classPath = routeClass.isAnnotationPresent(Route.class)
                ? routeClass.getAnnotation(Route.class).value()
                : "";
        List<RouteHandlerMetaInfo> infos = new ArrayList<>();
        for (Method method : routeClass.getMethods()) {
            String path = null;
            // 逐个探测五种请求方式注解,命中即记录其路径与对应的请求方式;命中多个由下方拒绝
            HttpMethod httpMethod = null;
            int hits = 0;
            if (method.isAnnotationPresent(Get.class)) {
                path = method.getAnnotation(Get.class).value();
                httpMethod = HttpMethod.GET;
                hits++;
            }
            if (method.isAnnotationPresent(Post.class)) {
                path = method.getAnnotation(Post.class).value();
                httpMethod = HttpMethod.POST;
                hits++;
            }
            if (method.isAnnotationPresent(Put.class)) {
                path = method.getAnnotation(Put.class).value();
                httpMethod = HttpMethod.PUT;
                hits++;
            }
            if (method.isAnnotationPresent(Patch.class)) {
                path = method.getAnnotation(Patch.class).value();
                httpMethod = HttpMethod.PATCH;
                hits++;
            }
            if (method.isAnnotationPresent(Delete.class)) {
                path = method.getAnnotation(Delete.class).value();
                httpMethod = HttpMethod.DELETE;
                hits++;
            }
            if (hits == 0) {
                continue;
            }
            if (hits > 1) {
                throw new IllegalArgumentException("Route method " + method.getName() + " declares multiple request method annotations");
            }
            // 三级一次合并:中间结果退化成根路径不算非法,只有最终结果退化成根路径才拒
            String url = PathHelper.mergePath(baseUrl, classPath, path);
            infos.add(new RouteHandlerMetaInfo(url, routeInstance, routeClass, method, httpMethod));
        }
        infos.sort(Comparator.comparing(RouteHandlerMetaInfo::url)
                .thenComparing(info -> info.method().getName())
                .thenComparing(info -> Arrays.toString(info.method().getParameterTypes())));
        return infos;
    }
}
