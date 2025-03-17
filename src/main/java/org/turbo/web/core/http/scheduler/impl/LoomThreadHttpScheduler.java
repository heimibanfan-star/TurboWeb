package org.turbo.web.core.http.scheduler.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.Promise;
import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.router.dispatcher.HttpDispatcher;
import org.turbo.web.core.http.handler.ExceptionHandlerDefinition;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.cookie.Cookies;
import org.turbo.web.core.http.request.HttpInfoRequest;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.session.SessionManagerProxy;
import org.turbo.web.core.http.sse.SSESession;
import org.turbo.web.exception.TurboExceptionHandlerException;
import org.turbo.web.exception.TurboSerializableException;
import org.turbo.web.lock.Locks;
import org.turbo.web.utils.http.HttpInfoRequestPackageUtils;
import org.turbo.web.utils.thread.LoomThreadUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * 使用虚拟县城的阻塞线程调度器
 */
public class LoomThreadHttpScheduler extends AbstractHttpScheduler {

    public LoomThreadHttpScheduler(
        SessionManagerProxy sessionManagerProxy,
        Middleware chain,
        ExceptionHandlerMatcher exceptionHandlerMatcher,
        ServerParamConfig config
    ) {
        super(
            sessionManagerProxy,
            chain,
            exceptionHandlerMatcher,
            config,
            LoomThreadHttpScheduler.class
        );
    }

    @Override
    public void execute(FullHttpRequest request, Promise<HttpResponse> promise, SSESession session) {
        LoomThreadUtils.execute(() -> {
            if (showRequestLog) {
                long startTime = System.currentTimeMillis();
                try {
                    try {
                        HttpResponse response = doExecute(request, session);
                        promise.setSuccess(response);
                    } catch (Throwable throwable) {
                        promise.setFailure(throwable);
                    }
                } finally {
                    log(request, System.currentTimeMillis() - startTime);
                }
            } else {
                try {
                    HttpResponse response = doExecute(request, session);
                    promise.setSuccess(response);
                } catch (Throwable throwable) {
                    promise.setFailure(throwable);
                }
            }
        });
    }

    private HttpResponse doExecute(FullHttpRequest request, SSESession session) {
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
            HttpContext context = new HttpContext(httpInfoRequest, response, sentinelMiddleware, session);
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
        // 获取异常定义信息
        ExceptionHandlerDefinition definition = matchExceptionHandlerDefinition(e);
        HttpInfoResponse response = new HttpInfoResponse(request.protocolVersion(), definition.getHttpResponseStatus());
        try {
            // 调用异常处理器
            Object result = doHandleException(definition, e);
            // 序列化内容
            response.setContent(objectMapper.writeValueAsString(result));
            response.setContentType("application/json;charset=utf-8");
            return response;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            response.release();
            throw new TurboExceptionHandlerException("异常处理器中出现错误：" + ex.getMessage());
        } catch (JsonProcessingException ex) {
            response.release();
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
    private HttpResponse handleResponse(Object result, HttpContext ctx) {
        // 判断是否写入内容
        if (ctx.isWrite()) {
            return ctx.getResponse();
        }
        // 如果为空返回空内容
        switch (result) {
            case null -> {
                ctx.text("");
                return ctx.getResponse();
            }
            // 判断返回值是否是响应对象
            case HttpResponse httpResponse -> {
                // 判断是否需要释放内存
                if (ctx.getResponse() != httpResponse) {
                    ctx.getResponse().release();
                }
                return httpResponse;
            }
            // 处理字符串类型
            case String s -> {
                // 写入ctx
                ctx.text(s);
                return ctx.getResponse();
            }
            default -> {
                // 其他类型作为json写入
                ctx.json(result);
                return ctx.getResponse();
            }
        }
    }
}
