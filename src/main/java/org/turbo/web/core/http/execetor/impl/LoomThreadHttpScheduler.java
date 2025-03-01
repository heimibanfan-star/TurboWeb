package org.turbo.web.core.http.execetor.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.Promise;
import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.execetor.HttpDispatcher;
import org.turbo.web.core.http.handler.ExceptionHandlerDefinition;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.cookie.Cookies;
import org.turbo.web.core.http.request.HttpInfoRequest;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.session.SessionManagerProxy;
import org.turbo.web.exception.TurboExceptionHandlerException;
import org.turbo.web.exception.TurboNotCatchException;
import org.turbo.web.exception.TurboSerializableException;
import org.turbo.web.lock.Locks;
import org.turbo.web.utils.common.BeanUtils;
import org.turbo.web.utils.http.HttpInfoRequestPackageUtils;
import org.turbo.web.utils.thread.LoomThreadUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 使用虚拟县城的阻塞线程调度器
 */
public class LoomThreadHttpScheduler extends AbstractHttpScheduler {

    private final ObjectMapper objectMapper = BeanUtils.getObjectMapper();

    public LoomThreadHttpScheduler(
        HttpDispatcher httpDispatcher,
        SessionManagerProxy sessionManagerProxy,
        Class<?> mainClass,
        List<Middleware> middlewares,
        ExceptionHandlerMatcher exceptionHandlerMatcher,
        ServerParamConfig config
    ) {
        super(
            httpDispatcher,
            sessionManagerProxy,
            mainClass,
            middlewares,
            exceptionHandlerMatcher,
            config
        );
    }

    @Override
    public void execute(FullHttpRequest request, Promise<HttpInfoResponse> promise) {
        LoomThreadUtils.execute(() -> {
            if (showRequestLog) {
                long startTime = System.currentTimeMillis();
                try {
                    try {
                        HttpInfoResponse response = doExecute(request);
                        promise.setSuccess(response);
                    } catch (Throwable throwable) {
                        promise.setFailure(throwable);
                    }
                } finally {
                    log(request, System.currentTimeMillis() - startTime);
                }
            } else {
                try {
                    HttpInfoResponse response = doExecute(request);
                    promise.setSuccess(response);
                } catch (Throwable throwable) {
                    promise.setFailure(throwable);
                }
            }
        });
    }

    private HttpInfoResponse doExecute(FullHttpRequest request) {
        // 添加读锁
        Locks.SESSION_LOCK.readLock().lock();
        HttpInfoRequest httpInfoRequest = null;
        HttpInfoResponse response = null;
        try {
             httpInfoRequest = HttpInfoRequestPackageUtils.packageRequest(request);
            // 初始化session
            Cookies cookies = httpInfoRequest.getCookies();
            String jsessionid = cookies.getCookie("JSESSIONID");
            initSession(httpInfoRequest, jsessionid);
            // 创建响应对象
            response = new HttpInfoResponse(request.protocolVersion(), HttpResponseStatus.OK);
            HttpContext context = new HttpContext(httpInfoRequest, response, sentinelMiddleware);
            Object result = context.doNext();
            // 处理session的结果
            handleSessionAfterRequest(context, jsessionid);
            // 处理响应数据
            return handleResponse(result, context);
        } catch (Throwable e) {
            // 释放内存
            if (response != null) {
                response.release();
            }
            return handleException(request, e);
        } finally {
            // 释放读锁
            Locks.SESSION_LOCK.readLock().unlock();
            if (httpInfoRequest != null) {
                releaseFileUploads(httpInfoRequest);
            }
        }
    }

    /**
     * 处理非捕获的异常
     *
     * @param request 请求对象
     * @param e 异常
     * @return 响应对象
     */
    private HttpInfoResponse handleException(FullHttpRequest request, Throwable e) {
        // 获取异常处理器的定义信息
        ExceptionHandlerDefinition definition = exceptionHandlerMatcher.match(e.getClass());
        // 判断是否获取到
        if (definition == null) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new TurboNotCatchException(e.getMessage(), e);
        }
        // 获取异常处理器实例
        Object handler = exceptionHandlerMatcher.getInstance(definition.getHandlerClass());
        if (handler == null) {
            throw new TurboExceptionHandlerException("未获取到异常处理器实例");
        }
        // 调用异常处理器
        try {
            HttpInfoResponse response = new HttpInfoResponse(request.protocolVersion(), definition.getHttpResponseStatus());
            Method method = definition.getMethod();
            Object result = method.invoke(handler, e);
            // 序列化内容
            response.setContent(objectMapper.writeValueAsString(result));
            response.setContentType("application/json;charset=utf-8");
            return response;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            log.error("异常处理器中调用方法时出现错误" + e);
            throw new TurboExceptionHandlerException("异常处理器中出现错误：" + ex.getMessage());
        } catch (JsonProcessingException ex) {
            log.error("序列化失败", ex);
            throw new TurboSerializableException(ex.getMessage());
        }
    }

    /**
     * 处理响应对象
     *
     * @param result 返回值
     * @param ctx 上下文对象
     * @return org.turbo.web.core.http.response.HttpInfoResponse
     */
    private HttpInfoResponse handleResponse(Object result, HttpContext ctx) {
        // 判断是否写入内容
        if (ctx.isWrite()) {
            return ctx.getResponse();
        }
        // 判断返回值是否是响应对象
        if (result instanceof HttpInfoResponse httpInfoResponse) {
            // 判断是否需要释放内存
            if (ctx.getResponse() != httpInfoResponse) {
                ctx.getResponse().release();
            }
            return httpInfoResponse;
        }
        // 处理字符串类型
        if (result instanceof String s) {
            // 写入ctx
            ctx.text(s);
            return ctx.getResponse();
        }
        // 其他类型作为json写入
        ctx.json(result);
        return ctx.getResponse();
    }
}
