package top.turboweb.http.middleware.router.container;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.router.container.info.MethodRouterDefinition;
import top.turboweb.commons.anno.*;
import top.turboweb.commons.exception.TurboControllerCreateException;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.commons.utils.base.TypeUtils;
import top.turboweb.http.middleware.router.container.info.TrieRouterInfo;

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
    public static RouterContainer initContainer(Collection<ControllerAttribute> controllerAttributes) {
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
                initRouterDefinition(routerContainer, method, aClass);
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
    private static void initRouterDefinition(RouterContainer container, Method method, Class<?> clazz) {
        RequestPath annotation = clazz.getAnnotation(RequestPath.class);
        String prePath = annotation.value();
        if (prePath == null || prePath.isEmpty()) {
            prePath = "/";
        }
        if (!prePath.startsWith("/")) {
            prePath = "/" + prePath;
        }
        if (prePath.endsWith("/")) {
            prePath = prePath.substring(0, prePath.length() - 1);
        }
        // 获取方法的注解
        if (method.isAnnotationPresent(Get.class)) {
            String path = prePath + method.getAnnotation(Get.class).value();
            doInitRouterDefinition(container, method, path);
        } else if (method.isAnnotationPresent(Post.class)) {
            String path = prePath + method.getAnnotation(Post.class).value();
            doInitRouterDefinition(container, method, path);
        } else if (method.isAnnotationPresent(Put.class)) {
            String path = prePath + method.getAnnotation(Put.class).value();
            doInitRouterDefinition(container, method, path);
        } else if (method.isAnnotationPresent(Patch.class)) {
            String path = prePath + method.getAnnotation(Patch.class).value();
            doInitRouterDefinition(container, method, path);
        } else if (method.isAnnotationPresent(Delete.class)) {
            String path = prePath + method.getAnnotation(Delete.class).value();
            doInitRouterDefinition(container, method, path);
        }
    }

    private static void doInitRouterDefinition(RouterContainer container, Method method, String path) {
        if (path.contains("*")) {
            throw new TurboRouterDefinitionCreateException("路径不能包含*");
        }
        if (path.isEmpty()) {
            throw new TurboRouterDefinitionCreateException("组合后的路径不能为空路径");
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        // 判断参数中是否存在http数据交换上下文
        Parameter[] parameters = method.getParameters();
        if (parameters.length != 1) {
            throw new TurboRouterDefinitionCreateException("方法参数只能有一个HttpContext");
        }
        if (!parameters[0].getType().equals(HttpContext.class)) {
            throw new TurboRouterDefinitionCreateException("方法参数只能有一个HttpContext");
        }
        Object instanceObj = container.getControllerInstances().get(method.getDeclaringClass());
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            MethodHandle methodHandle = lookup.unreflect(method).bindTo(instanceObj);
            MethodRouterDefinition methodRouterDefinition = new MethodRouterDefinition(methodHandle);
            parsePathAndSaveDefinition(container, method, path, methodRouterDefinition);
        } catch (IllegalAccessException e) {
            throw new TurboRouterDefinitionCreateException(e);
        }
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

    /**
     * 解析路径并保存定义
     *
     * @param container 路由容器
     * @param method    方法
     * @param path      路径
     * @param definition 路由方法定义
     */
    private static void parsePathAndSaveDefinition(RouterContainer container, Method method, String path, MethodRouterDefinition definition) {
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
}
