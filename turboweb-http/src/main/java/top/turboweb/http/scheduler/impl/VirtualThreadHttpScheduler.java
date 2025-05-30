package top.turboweb.http.scheduler.impl;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.cookie.Cookies;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.http.response.IgnoredHttpResponse;
import top.turboweb.http.response.sync.InternalSseEmitter;
import top.turboweb.http.response.sync.SseEmitter;
import top.turboweb.http.session.DefaultHttpSession;
import top.turboweb.http.session.HttpSession;
import top.turboweb.http.session.SessionManagerHolder;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.commons.lock.Locks;
import top.turboweb.http.handler.ExceptionHandlerSchedulerHelper;
import top.turboweb.http.request.HttpInfoRequestPackageHelper;
import top.turboweb.commons.utils.thread.VirtualThreadUtils;

/**
 * 使用虚拟县城的阻塞线程调度器
 */
public class VirtualThreadHttpScheduler extends AbstractHttpScheduler {

    public VirtualThreadHttpScheduler(
        SessionManagerHolder sessionManagerHolder,
        Middleware chain,
        ExceptionHandlerMatcher exceptionHandlerMatcher
    ) {
        super(
                sessionManagerHolder,
            chain,
            exceptionHandlerMatcher,
            VirtualThreadHttpScheduler.class
        );
    }

    @Override
    public void execute(FullHttpRequest request, ConnectSession session) {
        VirtualThreadUtils.execute(() -> {
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
             httpInfoRequest = HttpInfoRequestPackageHelper.packageRequest(request);
            // 初始化session
            Cookies cookies = httpInfoRequest.getCookies();
            String originSessionId = cookies.getCookie("JSESSIONID");
            HttpSession httpSession = new DefaultHttpSession(sessionManagerHolder.getSessionManager(), originSessionId);
            // 创建响应对象
            response = new HttpInfoResponse(request.protocolVersion(), HttpResponseStatus.OK);
            HttpContext context = new FullHttpContext(httpInfoRequest, httpSession, response, session);
            Object result = sentinelMiddleware.invoke(context);
            // 处理响应数据
            HttpResponse resultResponse = handleResponse(result, context);
            // 处理session
            handleSessionAfterRequest(httpSession, resultResponse, originSessionId);
            return resultResponse;
        } catch (Throwable e) {
            return ExceptionHandlerSchedulerHelper.doHandleForLoomScheduler(exceptionHandlerMatcher, response, e);
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
                // 如果是sse发射器直接忽略
                if (httpResponse instanceof SseEmitter sseEmitter) {
                    // 初始化sse发射器
                    InternalSseEmitter internalSseEmitter = (InternalSseEmitter) sseEmitter;
                    internalSseEmitter.initSse();
                    httpResponse = IgnoredHttpResponse.ignore();
                }
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
