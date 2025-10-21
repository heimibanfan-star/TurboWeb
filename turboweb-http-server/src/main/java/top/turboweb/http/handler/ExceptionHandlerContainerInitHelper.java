package top.turboweb.http.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.anno.ExceptionHandler;
import top.turboweb.anno.ExceptionResponseStatus;
import top.turboweb.commons.exception.TurboExceptionHandlerException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * 异常处理器容器初始化工具类。
 * <p>
 * 用于在框架启动时扫描所有带有 {@link ExceptionHandler} 注解的方法，
 * 将其封装为 {@link ExceptionHandlerDefinition} 并注册到 {@link ExceptionHandlerContainer} 中。
 * <br>
 * 支持识别 {@link ExceptionResponseStatus} 注解以设置异常处理后的 HTTP 状态码。
 * </p>
 */
public class ExceptionHandlerContainerInitHelper {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlerContainerInitHelper.class);

    private ExceptionHandlerContainerInitHelper() {
    }

    /**
     * 初始化异常处理器容器。
     * <p>
     * 遍历传入的异常处理器对象集合，扫描其中带有 {@link ExceptionHandler} 注解的公有方法，
     * 并创建对应的 {@link ExceptionHandlerDefinition} 注册到容器中。
     * </p>
     *
     * @param exceptionHandlerClassList 异常处理器对象列表
     * @return 构建完成的 {@link ExceptionHandlerContainer}
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
     * 通过无参构造方法创建类实例。
     * <p>
     * 若类中不存在无参构造方法，或实例化失败，则抛出 {@link TurboExceptionHandlerException}。
     * </p>
     *
     * @param clazz 目标类
     * @return 类的实例对象
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
     * 初始化异常处理器定义。
     * <p>
     * 根据方法及其注解信息，创建对应的 {@link ExceptionHandlerDefinition}。
     * 若方法带有 {@link ExceptionResponseStatus} 注解，则设置对应的 HTTP 状态码。
     * </p>
     *
     * @param method   异常处理方法
     * @param instance 方法所属的实例对象
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
