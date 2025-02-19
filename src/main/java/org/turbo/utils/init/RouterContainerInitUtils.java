package org.turbo.utils.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.anno.*;
import org.turbo.core.http.context.HttpContext;
import org.turbo.core.router.container.AnnoRouterContainer;
import org.turbo.core.router.container.RouterContainer;
import org.turbo.core.router.definition.RouterMethodDefinition;
import org.turbo.exception.TurboControllerCreateException;
import org.turbo.exception.TurboRouterDefinitionCreateException;
import org.turbo.utils.common.TypeUtils;

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
    public static RouterContainer initContainer(List<Class<?>> controllerList) {
        RouterContainer routerContainer = new AnnoRouterContainer();
        for (Class<?> aClass : controllerList) {
            // 判断类上是否有注解
            RequestPath annotation = aClass.getAnnotation(RequestPath.class);
            if (annotation == null) {
                throw new TurboControllerCreateException("类上没有RequestPath注解");
            }
            // 创建类的实例
            Object instance = createInstance(aClass);
            routerContainer.getControllerInstances().put(aClass, instance);
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
     * 创建实例
     *
     * @param clazz 类
     * @return 实例
     */
    private static Object createInstance(Class<?> clazz) {
        try {
            // 获取无参构造方法
            Constructor<?> constructor = clazz.getConstructor();
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            log.error("类上没有无参构造方法", e);
            throw new TurboControllerCreateException(e.getMessage());
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            log.error("controller实例创建失败：{}", clazz, e);
            throw new TurboControllerCreateException(e.getMessage());
        }
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
        // 判断参数中是否存在http数据交换上下文
        Parameter[] parameters = method.getParameters();
        if (parameters.length != 1) {
            throw new TurboRouterDefinitionCreateException("方法参数只能有一个HttpContext");
        }
        if (!parameters[0].getType().equals(HttpContext.class)) {
            throw new TurboRouterDefinitionCreateException("方法参数只能有一个HttpContext");
        }
        RouterMethodDefinition definition = new RouterMethodDefinition(method.getDeclaringClass(), method);
        parsePathAndSaveDefinition(container, method, path, definition);
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
            if (subStr.endsWith("/")) {
                subStr = subStr.substring(0, subStr.length() - 1);
            }
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
