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

    private static final String PATH_PARAM_PATTERN = "^(/(\\{[^/]+}))+$";
    private static final String PATH_VALUE_SEARCH_PATTERN = "\\{(.*?)}";
    private static final Pattern PATH_PARAM_PARSE = Pattern.compile(PATH_VALUE_SEARCH_PATTERN);

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
                // 判断方法的权限是否是公开
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                // 判断方法上是否有注解
                if (method.getAnnotations().length == 0) {
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
        Get getAnno = method.getAnnotation(Get.class);
        if (getAnno != null) {
            String path = prePath + getAnno.value();
            doInitRouterDefinition(container, method, path);
            return;
        }
        Post postAnno = method.getAnnotation(Post.class);
        if (postAnno != null) {
            String path = prePath + postAnno.value();
            doInitRouterDefinition(container, method, path);
            return;
        }
        Put putAnno = method.getAnnotation(Put.class);
        if (putAnno != null) {
            String path = prePath + putAnno.value();
            doInitRouterDefinition(container, method, path);
            return;
        }
        Delete deleteAnno = method.getAnnotation(Delete.class);
        if (deleteAnno != null) {
            String path = prePath + deleteAnno.value();
            doInitRouterDefinition(container, method, path);
            return;
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
                ParameterDefinition definition = new ParameterDefinition();
                PathParam anno = parameter.getAnnotation(PathParam.class);
                definition.setName(anno.value());
                definition.setType(parameter.getType());
                definition.setVariableType(ParameterType.PATH);
                routerMethodDefinition.addVariable(definition);
                continue;
            }
            // 封装请求体的参数
            if (parameter.isAnnotationPresent(BodyParam.class)) {
                Class<?> type = parameter.getType();
                BodyParam bodyParam = parameter.getAnnotation(BodyParam.class);
                if (type.isPrimitive() || isWrapperType(type.getName())) {
                    if ("".equals(bodyParam.value())) {
                        String className = method.getDeclaringClass().getName();
                        String methodName = method.getName();
                        throw new TurboRouterDefinitionCreateException("class:%s, method:%s, BodyParam是基本类型，但注解的value为空".formatted(className, methodName));
                    }
                }
                ParameterDefinition definition = new ParameterDefinition();
                definition.setName(bodyParam.value());
                definition.setType(parameter.getType());
                definition.setVariableType(ParameterType.BODY);
                routerMethodDefinition.addVariable(definition);
                continue;
            }
            // 处理查询参数
            Class<?> type = parameter.getType();
            if (type.isPrimitive() || isWrapperType(type.getName())) {
                QueryParam queryParam = parameter.getAnnotation(QueryParam.class);
                if (queryParam == null || "".equals(queryParam.value())) {
                    String className = method.getDeclaringClass().getName();
                    String methodName = method.getName();
                    throw new TurboRouterDefinitionCreateException("class:%s, method:%s, QueryParam是基本类型，但注解的value为空".formatted(className, methodName));
                }
            }
            ParameterDefinition definition = new ParameterDefinition();
            QueryParam queryParam = parameter.getAnnotation(QueryParam.class);
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
            if (!subStr.matches(PATH_PARAM_PATTERN)) {
                throw new TurboRouterDefinitionCreateException("路径参数格式错误: %s".formatted(path));
            }
            // 解析出所有的参数
            Matcher matcher = PATH_PARAM_PARSE.matcher(subStr);
            while (matcher.find()) {
                String param = matcher.group(1);
                if (definition.getPathParameters().contains(param)) {
                    throw new TurboRouterDefinitionCreateException("路径参数重复: %s".formatted(path));
                }
                definition.getPathParameters().add(param);
            }
            // 将路径修改为正则表达式模板
            path = path.replaceAll(PATH_VALUE_SEARCH_PATTERN, "([^/]*)");
            definition.setPattern(Pattern.compile(path));
            definition.setPathVariableCount(definition.getPathParameters().size());
            container.addPathRouter(type, path, definition);
            return;
        }
        container.addExtraRouter(type, path, definition);
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
