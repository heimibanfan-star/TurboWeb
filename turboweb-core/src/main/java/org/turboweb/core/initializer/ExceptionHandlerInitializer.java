package org.turboweb.core.initializer;

import org.turboweb.core.http.handler.ExceptionHandlerMatcher;

/**
 * 异常处理器初始化器
 */
public interface ExceptionHandlerInitializer {

    /**
     * 添加异常处理器
     *
     * @param exceptionHandler 异常处理器
     */
    void addExceptionHandler(Object... exceptionHandler);

    /**
     * 异常处理器的初始化器
     *
     * @return 异常处理器匹配器
     */
    ExceptionHandlerMatcher init();
}
