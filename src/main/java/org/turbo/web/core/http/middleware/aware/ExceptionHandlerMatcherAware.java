package org.turbo.web.core.http.middleware.aware;

import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;

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
