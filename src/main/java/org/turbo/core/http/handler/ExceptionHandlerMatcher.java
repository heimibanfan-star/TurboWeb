package org.turbo.core.http.handler;

/**
 * 异常处理器匹配器接口
 */
public interface ExceptionHandlerMatcher {

    /**
     * 匹配异常处理器
     *
     * @param exceptionClass 异常类
     * @return 异常处理器定义
     */
    ExceptionHandlerDefinition match(Class<? extends Throwable> exceptionClass);

    /**
     * 获取异常处理器实例
     *
     * @param clazz 异常处理器类
     * @return 异常处理器实例
     */
    Object getInstance(Class<?> clazz);
}
