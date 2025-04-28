package org.turbo.web.utils.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.anno.*;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.router.container.AnnoRouterContainer;
import org.turbo.web.core.http.router.container.RouterContainer;
import org.turbo.web.core.http.router.definition.RouterMethodDefinition;
import org.turbo.web.exception.TurboControllerCreateException;
import org.turbo.web.exception.TurboRouterDefinitionCreateException;
import org.turbo.web.utils.common.TypeUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路由定义信息初始化工具
 */
public class RouterContainerInitUtils {

    private static final String PATH_VAR_CHECK_REGEX = "^(/(\\{[^/]+}))+$";
    private static final String PATH_VALUE_SEARCH_REGEX = "\\{(.*?)}";
    private static final Pattern PATH_VALUE_GET_PATTERN = Pattern.compile(PATH_VALUE_SEARCH_REGEX);
    private static final Logger log = LoggerFactory.getLogger(RouterContainerInitUtils.class);

    private RouterContainerInitUtils() {
    }

    /**
     * 初始化路由容器
     *
     * @param controllerList 控制器类集合
     * @return 路由容器
     */
    public static RouterContainer initContainer(List<Object> controllerList) {
        RouterContainer routerContainer = new AnnoRouterContainer();
        for (Object controller : controllerList) {
            // 判断类上是否有注解
            Class<?> aClass = controller.getClass();
            RequestPath annotation = aClass.getAnnotation(RequestPath.class);
            if (annotation == null) {
                throw new TurboControllerCreateException("类上没有RequestPath注解:" + aClass);
            }
            routerContainer.getControllerInstances().put(aClass, controller);
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
        if (prePath.endsWith("/")) {
            prePath = prePath.substring(0, prePath.length() - 1);
        }
        // 获取方法的注解
        if (method.isAnnotationPresent(Get.class)) {
            String path = prePath + method.getAnnotation(Get.class).value();
            doInitRouterDefinition(container, method, path);
            return;
        }
        if (method.isAnnotationPresent(Post.class)) {
            String path = prePath + method.getAnnotation(Post.class).value();
            doInitRouterDefinition(container, method, path);
            return;
        }
        if (method.isAnnotationPresent(Put.class)) {
            String path = prePath + method.getAnnotation(Put.class).value();
            doInitRouterDefinition(container, method, path);
            return;
        }
        if (method.isAnnotationPresent(Delete.class)) {
            String path = prePath + method.getAnnotation(Delete.class).value();
            doInitRouterDefinition(container, method, path);
        }
    }

    private static void doInitRouterDefinition(RouterContainer container, Method method, String path) {
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
            RouterMethodDefinition definition = new RouterMethodDefinition(method.getDeclaringClass(), methodHandle);
            parsePathAndSaveDefinition(container, method, path, definition);
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
    private static void parsePathAndSaveDefinition(RouterContainer container, Method method, String path, RouterMethodDefinition definition) {
        String type;
        if (method.isAnnotationPresent(Get.class)) {
            type = "GET";
        } else if (method.isAnnotationPresent(Post.class)) {
            type = "POST";
        } else if (method.isAnnotationPresent(Put.class)) {
            type = "PUT";
        } else if (method.isAnnotationPresent(Delete.class)) {
            type = "DELETE";
        } else {
            throw new TurboRouterDefinitionCreateException("未知的路由类型");
        }
        int index = path.indexOf("{");
        if (index != -1) {
            String subStr = path.substring(index - 1);
            // 判断是否符合路径参数格式
            if (!subStr.matches(PATH_VAR_CHECK_REGEX)) {
                throw new TurboRouterDefinitionCreateException("路径参数格式错误: %s".formatted(path));
            }
            // 解析出所有的参数
            Matcher matcher = PATH_VALUE_GET_PATTERN.matcher(subStr);
            while (matcher.find()) {
                String param = matcher.group(1);
                if (definition.getPathParameters().contains(param)) {
                    throw new TurboRouterDefinitionCreateException("路径参数重复: %s".formatted(path));
                }
                definition.getPathParameters().add(param);
            }
            // 将路径修改为正则表达式模板
            path = path.replaceAll(PATH_VALUE_SEARCH_REGEX, "([^/]*)");
            definition.setPattern(Pattern.compile(path));
            definition.setPathVariableCount(definition.getPathParameters().size());
            container.addPathRouter(type, path, definition);
            return;
        }
        container.addCompleteRouter(type, path, definition);
    }
}
