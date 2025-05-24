package org.turboweb.http.handler;

import org.turboweb.commons.exception.TurboExceptionHandlerException;

import java.util.HashMap;
import java.util.Map;

/**
 * 异常处理器的容器
 */
public class ExceptionHandlerContainer {

    /**
     * 异常处理器定义
     */
    private final Map<Class<? extends Throwable>, ExceptionHandlerDefinition> exceptionHandlerDefinitions = new HashMap<>(1);

    /**
     * 添加异常处理器
     *
     * @param exceptionHandlerDefinition 异常处理器定义
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
     * 获取异常处理器定义
     *
     * @return 异常处理器定义
     */
    public Map<Class<? extends Throwable>, ExceptionHandlerDefinition> getExceptionHandlerDefinitions() {
        return exceptionHandlerDefinitions;
    }

    /**
     * 获取异常处理器定义
     *
     * @param exceptionClass 异常类
     * @return 异常处理器定义
     */
    public ExceptionHandlerDefinition getExceptionHandlerDefinition(Class<? extends Throwable> exceptionClass) {
        return exceptionHandlerDefinitions.get(exceptionClass);
    }
}
