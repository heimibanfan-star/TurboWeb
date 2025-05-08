package org.turbo.web.core.http.handler;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

/**
 * 异常处理器的定义信息
 */
public class ExceptionHandlerDefinition {

    private final Class<?> handlerClass;
    private final MethodHandle methodHandle;
    private final Class<? extends Throwable> exceptionClass;
    private HttpResponseStatus httpResponseStatus = HttpResponseStatus.OK;

    public ExceptionHandlerDefinition(Class<?> handlerClass, MethodHandle methodHandle, Class<? extends Throwable> exceptionClass) {
        this.handlerClass = handlerClass;
        this.methodHandle = methodHandle;
        this.exceptionClass = exceptionClass;
    }

    public void setHttpResponseStatus(HttpResponseStatus httpResponseStatus) {
        this.httpResponseStatus = httpResponseStatus;
    }

    public MethodHandle getMethodHandler() {
        return methodHandle;
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
