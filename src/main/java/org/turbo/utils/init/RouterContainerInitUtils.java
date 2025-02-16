package org.turbo.utils.init;

import org.turbo.anno.*;
import org.turbo.constants.ParameterType;
import org.turbo.core.router.container.AnnoRouterContainer;
import org.turbo.core.router.container.RouterContainer;
import org.turbo.core.router.definition.ParameterDefinition;
import org.turbo.core.router.definition.RouterMethodDefinition;
import org.turbo.exception.TurboControllerCreateException;
import org.turbo.exception.TurboRouterDefinitionCreateException;

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

    private RouterContainerInitUtils() {
    }

    /**
     * 初始化路由容器
     *
     * @param controllerList 控制器类集合
     * @return 路由容器
     */
    public static RouterContainer ininContainer(List<Class<?>> controllerList) {
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
            // 获取默认构造方法
            Constructor<?>[] constructors = clazz.getConstructors();
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == 0) {
                    return constructor.newInstance();
                }
            }
            throw new TurboControllerCreateException("没有默认构造方法");
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
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
        RouterMethodDefinition definition = createRouterMethodDefinition(method);
        parsePathAndSaveDefinition(container, method, path, definition);
    }

    /**
     * 创建路由方法定义
     *
     * @param method   方法
     * @return 路由方法定义
     */
    private static RouterMethodDefinition createRouterMethodDefinition(Method method) {
        RouterMethodDefinition routerMethodDefinition = new RouterMethodDefinition(method.getDeclaringClass());
        // 获取所有的参数列表
        Parameter[] parameters = method.getParameters();
        // 遍历参数
        for (Parameter parameter : parameters) {
            // 判断是否是路径参数
            if (parameter.isAnnotationPresent(PathParam.class)) {
                PathParam anno = parameter.getAnnotation(PathParam.class);
                String value = anno.value();
                if (value.isEmpty()) {
                    throw new TurboRouterDefinitionCreateException("路径参数不能为空");
                }
                ParameterDefinition definition = new ParameterDefinition(value, parameter.getType(), ParameterType.PATH);
                routerMethodDefinition.addVariable(definition);
                continue;
            }
            // 封装请求体的参数
            if (parameter.isAnnotationPresent(BodyParam.class)) {
                BodyParam anno = parameter.getAnnotation(BodyParam.class);
                // 对基本类型的参数进行校验
                checkForThePrimitiveType(parameter, anno.value(), method);
                ParameterDefinition definition = new ParameterDefinition(anno.value(), parameter.getType(), ParameterType.BODY);
                routerMethodDefinition.addVariable(definition);
                continue;
            }
            // 处理查询参数
            QueryParam queryParam = parameter.getAnnotation(QueryParam.class);
            // 处理基本类型的参数校验
            checkForThePrimitiveType(parameter, queryParam != null ? queryParam.value(): "", method);
            ParameterDefinition definition = new ParameterDefinition();
            if (queryParam == null) {
                definition.setName(parameter.getName());
            } else {
                definition.setName(queryParam.value());
            }
            definition.setType(parameter.getType());
            definition.setVariableType(ParameterType.QUERY);
            routerMethodDefinition.addVariable(definition);
        }
        return routerMethodDefinition;
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
        if (type.isPrimitive() || isWrapperType(type.getName())) {
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

    /**
     * 判断是否是包装类型
     *
     * @param className 类型
     * @return 是否是包装类型
     */
    private static boolean isWrapperType(String className) {
        return className.equals(Boolean.class.getName()) ||
            className.equals(Character.class.getName()) ||
            className.equals(Byte.class.getName()) ||
            className.equals(Short.class.getName()) ||
            className.equals(Integer.class.getName()) ||
            className.equals(Long.class.getName()) ||
            className.equals(Float.class.getName()) ||
            className.equals(Double.class.getName()) ||
            className.equals(Void.class.getName()) ||
            className.equals(String.class.getName());
    }
}
