package org.turboweb.core.http.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.http.response.HttpInfoResponse;
import org.turboweb.commons.exception.TurboExceptionHandlerException;
import org.turboweb.commons.exception.TurboRouterException;
import org.turboweb.commons.utils.base.BeanUtils;
import org.turboweb.commons.utils.base.HttpResponseUtils;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 执行异常处理器的工具类
 */
public class ExceptionHandlerSchedulerHelper {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlerSchedulerHelper.class);

    /**
     * 同步模型执行异常处理
     *
     * @param matcher 匹配器
     * @param response 响应
     * @param e 异常
     * @return 响应
     */
    public static HttpInfoResponse doHandleForLoomScheduler(ExceptionHandlerMatcher matcher, HttpInfoResponse response, Throwable e) {
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
                    newResponse.setContentType("application/json");
                    response.release();
                    return newResponse;
                }
            } catch (Throwable ex) {
                return doDefaultExceptionHandler(response, e);
            }
        }
        // 调用系统默认的异常处理器
        return doDefaultExceptionHandler(response, e);
    }

    /**
     * 反应式模型异常处理器
     *
     * @param matcher 匹配器
     * @param response 响应
     * @param e 异常
     * @return 响应
     */
    public static Mono<HttpInfoResponse> doHandleForReactiveScheduler(ExceptionHandlerMatcher matcher, HttpInfoResponse response, Throwable e) {
         return Mono.just(e)
            .flatMap(ex -> {
                ExceptionHandlerDefinition definition = matcher.match(ex.getClass());
                if (definition != null) {
                    MethodHandle methodHandler = definition.getMethodHandler();
                    try {
                        // 调用异常处理器
                        Object result = methodHandler.invoke(ex);
                        // 处理返回值
                        if (result instanceof HttpResponse) {
                            return Mono.error(new TurboExceptionHandlerException("异常处理器不允许返回io.netty.handler.codec.http.HttpResponse"));
                        } else if (result instanceof Mono<?> mono) {
                            return mono;
                        } else {
                            return Mono.just(result);
                        }
                    } catch (Throwable exc) {
                        return Mono.error(new TurboExceptionHandlerException("异常处理器调用失败", exc));
                    }
                } else {
                   return Mono.error(ex);
                }
            })
             .flatMap(result -> {
                 if (result instanceof HttpResponse) {
                     return Mono.error(new TurboExceptionHandlerException("异常处理器不允许返回io.netty.handler.codec.http.HttpResponse"));
                 } else {
                     try {
                         String json = BeanUtils.getObjectMapper().writeValueAsString(result);
                         HttpInfoResponse newResponse = new HttpInfoResponse(response.protocolVersion(), HttpResponseStatus.OK);
                         HttpResponseUtils.mergeHeaders(response, newResponse);
                         newResponse.setContent(json);
                         newResponse.setContentType("application/json");
                         response.release();
                         return Mono.just(newResponse);
                     } catch (JsonProcessingException ex) {
                         return Mono.error(ex);
                     }
                 }
             })
             .onErrorResume(ex -> Mono.just(doDefaultExceptionHandler(response, ex)));
    }

    /**
     * 默认的异常处理器
     *
     * @param response 响应
     * @param e 异常
     * @return 响应
     */
    private static HttpInfoResponse doDefaultExceptionHandler(HttpInfoResponse response, Throwable e) {
        try {
            log.error("业务逻辑处理失败", e);
            Map<String, String> errorMsg = new HashMap<>();
            if (e instanceof TurboRouterException exception && Objects.equals(exception.getCode(), TurboRouterException.ROUTER_NOT_MATCH)) {
                HttpInfoResponse newResponse = new HttpInfoResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                HttpResponseUtils.mergeHeaders(response, newResponse);
                errorMsg.put("code", "404");
                errorMsg.put("msg", e.getMessage());
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
