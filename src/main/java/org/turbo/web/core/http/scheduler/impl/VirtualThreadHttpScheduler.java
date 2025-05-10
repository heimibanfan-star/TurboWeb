package org.turbo.web.core.http.scheduler.impl;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.context.FullHttpContext;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.cookie.Cookies;
import org.turbo.web.core.http.request.HttpInfoRequest;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.session.SessionManagerProxy;
import org.turbo.web.core.connect.ConnectSession;
import org.turbo.web.lock.Locks;
import org.turbo.web.utils.handler.ExceptionHandlerSchedulerUtils;
import org.turbo.web.utils.http.HttpInfoRequestPackageUtils;
import org.turbo.web.utils.thread.LoomThreadUtils;

/**
 * 使用虚拟县城的阻塞线程调度器
 */
public class VirtualThreadHttpScheduler extends AbstractHttpScheduler {

    public VirtualThreadHttpScheduler(
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
            VirtualThreadHttpScheduler.class
        );
    }

    @Override
    public void execute(FullHttpRequest request, ConnectSession session) {
        LoomThreadUtils.execute(() -> {
            long startTime = System.nanoTime();
            try {
                HttpResponse response = doExecute(request, session);
                writeResponse(session, request, response, startTime);
            } finally {
                request.release();
            }
        });
    }

    private HttpResponse doExecute(FullHttpRequest request, ConnectSession session) {
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
            HttpContext context = new FullHttpContext(httpInfoRequest, response, session);
            Object result = sentinelMiddleware.invoke(context);
            // 处理session的结果
            handleSessionAfterRequest(context, jsessionid);
            // 处理响应数据
            return handleResponse(result, context);
        } catch (Throwable e) {
            return ExceptionHandlerSchedulerUtils.doHandleForLoomScheduler(exceptionHandlerMatcher, response, e);
        } finally {
            // 释放读锁
            Locks.SESSION_LOCK.readLock().unlock();
            if (httpInfoRequest != null) {
                releaseFileUploads(httpInfoRequest);
            }
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
