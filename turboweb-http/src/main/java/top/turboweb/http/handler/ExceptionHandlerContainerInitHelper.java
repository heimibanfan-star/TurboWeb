package top.turboweb.http.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.anno.ExceptionHandler;
import top.turboweb.commons.anno.ExceptionResponseStatus;
import top.turboweb.commons.exception.TurboExceptionHandlerException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * 异常处理器容器初始化工具
 */
public class ExceptionHandlerContainerInitHelper {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlerContainerInitHelper.class);

    private ExceptionHandlerContainerInitHelper() {
    }

    /**
     * 初始化异常处理器容器
     *
     * @param exceptionHandlerClassList 异常处理器类集合
     * @return 异常处理器容器
     */
    public static ExceptionHandlerContainer initContainer(List<Object> exceptionHandlerClassList) {
        ExceptionHandlerContainer container = new ExceptionHandlerContainer();
        for (Object handler : exceptionHandlerClassList) {
            Class<?> aClass = handler.getClass();
            // 获取所有的方法
            Method[] methods = aClass.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(ExceptionHandler.class) || !Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                ExceptionHandlerDefinition definition = initExceptionHandlerDefinition(method, handler);
                container.addExceptionHandler(definition);
            }
        }
        return container;
    }

    /**
     * 创建实例对象
     *
     * @param clazz 类
     * @return 实例对象
     */
    private static Object createInstance(Class<?> clazz) {
        try {
            Constructor<?> constructor = clazz.getConstructor();
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            log.error("类上没有无参构造方法", e);
            throw new TurboExceptionHandlerException(e.getMessage());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            log.error("异常处理器实例创建失败：{}", clazz, e);
            throw new TurboExceptionHandlerException(e.getMessage());
        }
    }

    /**
     * 初始化异常处理器定义
     *
     * @param method 方法
     * @return 异常处理器定义
     */
    private static ExceptionHandlerDefinition initExceptionHandlerDefinition(Method method, Object instance) {
        // 获取注解
        ExceptionHandler annotation = method.getAnnotation(ExceptionHandler.class);
        // 获取异常的类型
        Class<? extends Throwable> exceptionClass = annotation.value();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        ExceptionHandlerDefinition definition = null;
        try {
            MethodHandle methodHandle = lookup.unreflect(method).bindTo(instance);
            definition = new ExceptionHandlerDefinition(method.getDeclaringClass(), methodHandle, exceptionClass);
            // 判断是否存在状态码注解
            if (method.isAnnotationPresent(ExceptionResponseStatus.class)) {
                HttpResponseStatus status = HttpResponseStatus.valueOf(method.getAnnotation(ExceptionResponseStatus.class).value());
                definition.setHttpResponseStatus(status);
            }
        } catch (IllegalAccessException e) {
            throw new TurboExceptionHandlerException(e);
        }
        return definition;
    }
}
