package org.turboweb.core.http.middleware.aware;

import org.turboweb.core.http.handler.ExceptionHandlerMatcher;

/**
 * 注入异常匹配器的接口
 */
public interface ExceptionHandlerMatcherAware {

    /**
     * 注入异常处理器匹配器
     *
     * @param matcher 匹配器
     */
    void setExceptionHandlerMatcher(ExceptionHandlerMatcher matcher);
}
