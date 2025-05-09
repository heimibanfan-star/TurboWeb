package org.turbo.web.utils.handler;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.turbo.web.core.http.handler.ExceptionHandlerDefinition;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.exception.TurboExceptionHandlerException;
import org.turbo.web.exception.TurboMethodInvokeThrowable;
import org.turbo.web.utils.common.BeanUtils;
import org.turbo.web.utils.http.HttpResponseUtils;

import java.lang.invoke.MethodHandle;

/**
 * 执行异常处理器的工具类
 */
public class ExceptionHandlerSchedulerUtils {

    public static HttpResponse doHandle(ExceptionHandlerMatcher matcher, HttpInfoResponse response, Exception e) throws TurboMethodInvokeThrowable {
        // 匹配异常处理器的定义
        ExceptionHandlerDefinition definition = matcher.match(e.getClass());
        if (definition != null) {
            MethodHandle methodHandler = definition.getMethodHandler();
            try {
                Object result = methodHandler.invoke(e);
                if (result instanceof HttpResponse) {
                    throw new TurboExceptionHandlerException("异常处理器不允许返回io.netty.handler.codec.http.HttpResponse");
                } else {
                    String json = BeanUtils.getObjectMapper().writeValueAsString(result);
                    HttpInfoResponse newResponse = new HttpInfoResponse(response.protocolVersion(), definition.getHttpResponseStatus());
                    HttpResponseUtils.mergeHeaders(response, newResponse);
                    newResponse.setContent(json);
                    response.release();
                    return newResponse;
                }
            } catch (Throwable ex) {
                throw new TurboMethodInvokeThrowable(e);
            }
        }
        // 调用系统默认的异常处理器
        HttpInfoResponse newResponse = doDefaultExceptionHandler(response, e);
        if (newResponse != null) {
            return newResponse;
        }
        // 处理无法解决的异常问题
        newResponse = new HttpInfoResponse(response.protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
        HttpResponseUtils.mergeHeaders(response, newResponse);
        newResponse.setContent(e.getMessage());
        response.release();
        return newResponse;
    }

    private static HttpInfoResponse doDefaultExceptionHandler(HttpResponse response, Exception e) {

        return null;
    }
}
