package top.turboweb.http.scheduler.impl;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.commons.lock.Locks;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.cookie.Cookies;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.handler.ExceptionHandlerSchedulerHelper;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.http.request.HttpInfoRequestPackageHelper;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.http.response.negotiator.HttpResponseNegotiator;
import top.turboweb.http.response.negotiator.SyncHttpResponseNegotiator;
import top.turboweb.http.session.DefaultHttpSession;
import top.turboweb.http.session.HttpSession;
import top.turboweb.http.session.SessionManagerHolder;

/**
 * 同步模型的HTTP调度器
 */
public abstract class SyncHttpScheduler extends AbstractHttpScheduler{

    private final HttpResponseNegotiator httpResponseNegotiator;

    public SyncHttpScheduler(
            SessionManagerHolder sessionManagerHolder,
            Middleware chain,
            ExceptionHandlerMatcher exceptionHandlerMatcher,
            Class<?> subClass
    ) {
        super(sessionManagerHolder, chain, exceptionHandlerMatcher, subClass);
        this.httpResponseNegotiator = new SyncHttpResponseNegotiator();
    }

    /**
     * 异步运行任务
     * @param runnable 任务
     */
    protected abstract void runTask(Runnable runnable);

    @Override
    public void execute(FullHttpRequest request, ConnectSession session) {
        this.runTask(() -> {
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
            // 创建响应对象
            response = new HttpInfoResponse(request.protocolVersion(), HttpResponseStatus.OK);
            httpInfoRequest = HttpInfoRequestPackageHelper.packageRequest(request);
            // 初始化session
            Cookies cookies = httpInfoRequest.getCookies();
            String originSessionId = cookies.getCookie("JSESSIONID");
            HttpSession httpSession = new DefaultHttpSession(sessionManagerHolder.getSessionManager(), originSessionId);
            HttpContext context = new FullHttpContext(httpInfoRequest, httpSession, response, session);
            Object result = sentinelMiddleware.invoke(context);
            // 协商返回结果
            HttpResponse resultResponse = httpResponseNegotiator.negotiate(context, result);
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
}
