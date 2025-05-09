package org.turbo.web.utils.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.http.handler.ExceptionHandlerDefinition;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.request.HttpInfoRequest;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.exception.TurboExceptionHandlerException;
import org.turbo.web.exception.TurboMethodInvokeThrowable;
import org.turbo.web.exception.TurboRouterException;
import org.turbo.web.utils.common.BeanUtils;
import org.turbo.web.utils.http.HttpResponseUtils;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 执行异常处理器的工具类
 */
public class ExceptionHandlerSchedulerUtils {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlerSchedulerUtils.class);

    /**
     * 执行异常处理
     *
     * @param matcher 匹配器
     * @param request 请求
     * @param response 响应
     * @param e 异常
     * @return 响应
     */
    public static HttpResponse doHandle(ExceptionHandlerMatcher matcher, HttpInfoRequest request, HttpInfoResponse response, Exception e) throws TurboMethodInvokeThrowable {
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
        return doDefaultExceptionHandler(request, response, e);
    }

    /**
     * 默认的异常处理器
     *
     * @param request 请求
     * @param response 响应
     * @param e 异常
     * @return 响应
     */
    private static HttpInfoResponse doDefaultExceptionHandler(HttpInfoRequest request, HttpInfoResponse response, Exception e) {
        try {
            log.error("业务逻辑处理失败", e);
            Map<String, String> errorMsg = new HashMap<>();
            if (e instanceof TurboRouterException exception && Objects.equals(exception.getCode(), TurboRouterException.ROUTER_NOT_MATCH)) {
                HttpInfoResponse newResponse = new HttpInfoResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                HttpResponseUtils.mergeHeaders(response, newResponse);
                errorMsg.put("code", "404");
                errorMsg.put("msg", "Router Handler Not Found For: %s %s".formatted(request.getMethod(), request.getUri()));
                response.setContent(BeanUtils.getObjectMapper().writeValueAsString(errorMsg));
                response.setContentType("application/json");
                return response;
            } else {
                HttpInfoResponse newResponse = new HttpInfoResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                HttpResponseUtils.mergeHeaders(response, newResponse);
                errorMsg.put("code", "500");
                errorMsg.put("msg", e.getMessage());
                response.setContent(BeanUtils.getObjectMapper().writeValueAsString(errorMsg));
                response.setContentType("application/json");
                return response;
            }
        } catch (JsonProcessingException ex) {
            log.error("json序列化异常", ex);
            HttpInfoResponse newResponse = new HttpInfoResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            HttpResponseUtils.mergeHeaders(response, newResponse);
            newResponse.setContent("{\"code\":\"500\",\"msg\":\"异常结果封装失败：json序列化异常\"}");
            newResponse.setContentType("application/json");
            return newResponse;
        }
    }
}
