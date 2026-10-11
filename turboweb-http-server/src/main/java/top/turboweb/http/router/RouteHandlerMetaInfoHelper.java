package top.turboweb.http.router;

import top.turboweb.http.middleware.router.container.PathHelper;
import top.turboweb.rest.route.Delete;
import top.turboweb.rest.route.Get;
import top.turboweb.rest.route.Patch;
import top.turboweb.rest.route.Post;
import top.turboweb.rest.route.Put;
import top.turboweb.rest.route.Route;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
     * @throws IllegalArgumentException routeInstance 为 null;或某方法同时标注多个请求方式注解;
     *                                  或合并后的路径包含不允许的字符
     */
    public static List<RouteHandlerMetaInfo> parse(String baseUrl, Object routeInstance, Class<?> targetClass) {
        if (routeInstance == null) {
            throw new IllegalArgumentException("路由处理器实例不能为空");
        }
        Class<?> routeClass = targetClass != null ? targetClass : routeInstance.getClass();
        String classPath = routeClass.isAnnotationPresent(Route.class)
                ? routeClass.getAnnotation(Route.class).value()
                : "";
        String basePath = PathHelper.mergePath(baseUrl, classPath);
        List<RouteHandlerMetaInfo> infos = new ArrayList<>();
        for (Method method : routeClass.getMethods()) {
            String path = null;
            int hits = 0;
            if (method.isAnnotationPresent(Get.class)) {
                path = method.getAnnotation(Get.class).value();
                hits++;
            }
            if (method.isAnnotationPresent(Post.class)) {
                path = method.getAnnotation(Post.class).value();
                hits++;
            }
            if (method.isAnnotationPresent(Put.class)) {
                path = method.getAnnotation(Put.class).value();
                hits++;
            }
            if (method.isAnnotationPresent(Patch.class)) {
                path = method.getAnnotation(Patch.class).value();
                hits++;
            }
            if (method.isAnnotationPresent(Delete.class)) {
                path = method.getAnnotation(Delete.class).value();
                hits++;
            }
            if (hits == 0) {
                continue;
            }
            if (hits > 1) {
                throw new IllegalArgumentException("路由方法 " + method.getName() + " 同时标注了多个请求方式注解");
            }
            String url = PathHelper.mergePath(basePath, path);
            infos.add(new RouteHandlerMetaInfo(url, routeInstance, routeClass, method));
        }
        infos.sort(Comparator.comparing(RouteHandlerMetaInfo::url)
                .thenComparing(info -> info.method().getName())
                .thenComparing(info -> Arrays.toString(info.method().getParameterTypes())));
        return infos;
    }
}
