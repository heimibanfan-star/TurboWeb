package top.turboweb.http.handler;

import top.turboweb.commons.exception.TurboExceptionHandlerException;

import java.util.HashMap;
import java.util.Map;

/**
 * 异常处理器容器。
 * <p>
 * 该类用于存储和管理所有注册的异常处理器定义，
 * 提供添加、获取等基本操作。每种异常类型最多只能绑定一个异常处理器。
 * </p>
 */
public class ExceptionHandlerContainer {

    /**
     * 存储异常类型与对应异常处理器定义的映射表。
     * <p>键为异常类，值为对应的 {@link ExceptionHandlerDefinition}。</p>
     */
    private final Map<Class<? extends Throwable>, ExceptionHandlerDefinition> exceptionHandlerDefinitions = new HashMap<>(1);

    /**
     * 向容器中添加异常处理器定义。
     * <p>
     * 若同一异常类型的处理器已存在，则抛出 {@link TurboExceptionHandlerException}。
     * </p>
     *
     * @param exceptionHandlerDefinition 异常处理器定义
     * @throws TurboExceptionHandlerException 当存在重复异常处理器时抛出
     */
    public void addExceptionHandler(ExceptionHandlerDefinition exceptionHandlerDefinition) {
        // 判断是否有重复的异常处理器
        if (exceptionHandlerDefinitions.containsKey(exceptionHandlerDefinition.getExceptionClass())) {
            throw new TurboExceptionHandlerException("重复的异常处理器：" + exceptionHandlerDefinition.getExceptionClass().getName());
        }
        // 将异常处理器添加到容器中
        exceptionHandlerDefinitions.put(exceptionHandlerDefinition.getExceptionClass(), exceptionHandlerDefinition);
    }

    /**
     * 获取所有已注册的异常处理器定义。
     *
     * @return 异常处理器定义映射表
     */
    public Map<Class<? extends Throwable>, ExceptionHandlerDefinition> getExceptionHandlerDefinitions() {
        return exceptionHandlerDefinitions;
    }

    /**
     * 根据异常类型获取对应的异常处理器定义。
     *
     * @param exceptionClass 异常类
     * @return 匹配的异常处理器定义；若不存在则返回 {@code null}
     */
    public ExceptionHandlerDefinition getExceptionHandlerDefinition(Class<? extends Throwable> exceptionClass) {
        return exceptionHandlerDefinitions.get(exceptionClass);
    }
}
