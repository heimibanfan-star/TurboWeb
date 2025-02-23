package org.turbo.web.core.http.handler;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.lang.reflect.Method;

/**
 * 异常处理器的定义信息
 */
public class ExceptionHandlerDefinition {

    private final Class<?> handlerClass;
    private final Method method;
    private final Class<? extends Throwable> exceptionClass;
    private HttpResponseStatus httpResponseStatus = HttpResponseStatus.OK;

    public ExceptionHandlerDefinition(Class<?> handlerClass, Method method, Class<? extends Throwable> exceptionClass) {
        this.handlerClass = handlerClass;
        this.method = method;
        this.exceptionClass = exceptionClass;
    }

    public void setHttpResponseStatus(HttpResponseStatus httpResponseStatus) {
        this.httpResponseStatus = httpResponseStatus;
    }

    public Method getMethod() {
        return method;
    }

    public Class<? extends Throwable> getExceptionClass() {
        return exceptionClass;
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

    public Class<?> getHandlerClass() {
        return handlerClass;
    }
}
