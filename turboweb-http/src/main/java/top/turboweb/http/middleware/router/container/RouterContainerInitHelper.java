package top.turboweb.http.middleware.router.container;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.router.info.AutoBindRouterDefinition;
import top.turboweb.http.middleware.router.info.MethodRouterDefinition;
import top.turboweb.commons.anno.*;
import top.turboweb.commons.exception.TurboControllerCreateException;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.commons.utils.base.TypeUtils;
import top.turboweb.http.middleware.router.info.RouterDefinition;
import top.turboweb.http.middleware.router.info.TrieRouterInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.Collection;
import java.util.Objects;

/**
 * 路由定义信息初始化工具
 */
public class RouterContainerInitHelper {

    public static class ControllerAttribute {
        private final Object instance;
        private final Class<?> clazz;

        public ControllerAttribute(Object instance, Class<?> clazz) {
            this.instance = instance;
            this.clazz = clazz;
        }
    }

    private RouterContainerInitHelper() {
    }

    /**
     * 初始化路由容器
     *
     * @param controllerAttributes 控制器类集合
     * @return 路由容器
     */
    public static RouterContainer initContainer(Collection<ControllerAttribute> controllerAttributes, boolean autoBind) {
        RouterContainer routerContainer = new DefaultRouterContainer();
        for (ControllerAttribute controllerAttribute : controllerAttributes) {
            // 判断类上是否有注解
            Class<?> aClass = Objects.requireNonNullElseGet(controllerAttribute.clazz, controllerAttribute.instance::getClass);
            RequestPath annotation = aClass.getAnnotation(RequestPath.class);
            if (annotation == null) {
                throw new TurboControllerCreateException("类上没有RequestPath注解:" + aClass);
            }
            routerContainer.getControllerInstances().put(aClass, controllerAttribute.instance);
            // 获取类中所有的方法
            Method[] methods = aClass.getMethods();
            for (Method method : methods) {
                // 跳过非公开和没有注解的方法
                if (!Modifier.isPublic(method.getModifiers()) || method.getAnnotations().length == 0) {
                    continue;
                }
                // 初始化方法路由
                initRouterDefinition(routerContainer, method, aClass, autoBind);
            }
        }
        return routerContainer;
    }

    /**
     * 初始化路由定义
     *
     * @param container 路由容器
     * @param method    方法
     */
    private static void initRouterDefinition(RouterContainer container, Method method, Class<?> clazz, boolean autoBind) {
        RequestPath annotation = clazz.getAnnotation(RequestPath.class);
        String prePath = annotation.value();
        // 获取方法的注解
        if (method.isAnnotationPresent(Get.class)) {
            String path = PathHelper.mergePath(prePath, method.getAnnotation(Get.class).value());
            doInitRouterDefinition(container, method, path, autoBind);
        } else if (method.isAnnotationPresent(Post.class)) {
            String path = PathHelper.mergePath(prePath, method.getAnnotation(Post.class).value());
            doInitRouterDefinition(container, method, path, autoBind);
        } else if (method.isAnnotationPresent(Put.class)) {
            String path = PathHelper.mergePath(prePath, method.getAnnotation(Put.class).value());
            doInitRouterDefinition(container, method, path, autoBind);
        } else if (method.isAnnotationPresent(Patch.class)) {
            String path = PathHelper.mergePath(prePath, method.getAnnotation(Patch.class).value());
            doInitRouterDefinition(container, method, path, autoBind);
        } else if (method.isAnnotationPresent(Delete.class)) {
            String path = PathHelper.mergePath(prePath, method.getAnnotation(Delete.class).value());
            doInitRouterDefinition(container, method, path, autoBind);
        }
    }


    private static void doInitRouterDefinition(RouterContainer container, Method method, String path, boolean autoBind) {
        if (path.contains("*")) {
            throw new TurboRouterDefinitionCreateException("路径不能包含*");
        }
        // 获取当前方法所属的实例对象
        Object instance = container.getControllerInstances().get(method.getDeclaringClass());
        RouterDefinition routerDefinition;
        // 判断是否开启自动参数绑定
        if (autoBind) {
            routerDefinition = new AutoBindRouterDefinition(instance, method);
        } else {
            routerDefinition = new MethodRouterDefinition(instance, method);
        }
        parsePathAndSaveDefinition(container, method, path, routerDefinition);
    }

    /**
     * 解析路径并保存定义
     *
     * @param container 路由容器
     * @param method    方法
     * @param path      路径
     * @param definition 路由方法定义
     */
    private static void parsePathAndSaveDefinition(RouterContainer container, Method method, String path, RouterDefinition definition) {
        String type;
        if (method.isAnnotationPresent(Get.class)) {
            type = "GET";
        } else if (method.isAnnotationPresent(Post.class)) {
            type = "POST";
        } else if (method.isAnnotationPresent(Put.class)) {
            type = "PUT";
        } else if (method.isAnnotationPresent(Delete.class)) {
            type = "DELETE";
        } else if (method.isAnnotationPresent(Patch.class)) {
            type = "PATCH";
        } else {
            throw new TurboRouterDefinitionCreateException("未知的路由类型");
        }
        int index = path.indexOf("{");
        if (index != -1) {
            // 根据请求方式获取前缀树
            TrieRouterInfo trieRouterInfo = container.getTrieRouterInfo();
            // 添加到前缀树中
            trieRouterInfo.addRouter(type, path, definition);
        }
        container.getExactRouterInfo().addRouter(type, path, definition);
    }

    /**
     * 检查参数是否是基本类型
     *
     * @param parameter 参数
     * @param paramName 参数名
     * @param method    方法
     */
    private static void checkForThePrimitiveType(Parameter parameter, String paramName, Method method) {
        Class<?> type = parameter.getType();
        if (TypeUtils.isWrapperType(type.getName())) {
            if ("".equals(paramName)) {
                String className = method.getDeclaringClass().getName();
                String methodName = method.getName();
                throw new TurboRouterDefinitionCreateException("class:%s, method:%s, Param是基本类型，但注解不存在或注解value为空".formatted(className, methodName));
            }
        }
    }
}
