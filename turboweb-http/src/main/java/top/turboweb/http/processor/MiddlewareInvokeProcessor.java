package top.turboweb.http.processor;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.commons.lock.Locks;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.cookie.DefaultHttpCookieManager;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.processor.convertor.HttpResponseConverter;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.http.request.HttpInfoRequestPackageHelper;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.http.session.BackHoleSessionManager;
import top.turboweb.http.session.DefaultHttpSession;
import top.turboweb.http.session.HttpSession;
import top.turboweb.http.session.SessionManagerHolder;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 执行中间件的处理器
 */
public class MiddlewareInvokeProcessor extends Processor{

    private final Middleware chain;
    private final SessionManagerHolder sessionManagerHolder;
    private final HttpResponseConverter converter;

    public MiddlewareInvokeProcessor(Middleware chain, SessionManagerHolder sessionManagerHolder, HttpResponseConverter converter) {
        this.chain = chain;
        this.sessionManagerHolder = sessionManagerHolder;
        this.converter = converter;
    }

    @Override
    public HttpResponse invoke(FullHttpRequest fullHttpRequest, ConnectSession connectSession) {
        boolean needLock = !(sessionManagerHolder.getSessionManager() instanceof BackHoleSessionManager);
        if (needLock) {
            Locks.SESSION_LOCK.readLock().lock();
            try {
                return executeMiddleware(fullHttpRequest, connectSession);
            } finally {
                Locks.SESSION_LOCK.readLock().unlock();
            }
        } else {
            return executeMiddleware(fullHttpRequest, connectSession);
        }
    }

    /**
     * 执行中间件
     * @param fullHttpRequest 请求对象
     * @param connectSession session对象
     * @return 响应对象
     */
    private HttpResponse executeMiddleware(FullHttpRequest fullHttpRequest, ConnectSession connectSession) {
        // 封装请求对象
        HttpInfoRequest httpInfoRequest = HttpInfoRequestPackageHelper.packageRequest(fullHttpRequest);
        HttpSession httpSession = null;
        try {
            // 初始化Cookie
            HttpCookieManager cookieManager = new DefaultHttpCookieManager(fullHttpRequest.headers());
            // 初始化session
            String originSessionId = cookieManager.getCookie("JSESSIONID");
            httpSession = new DefaultHttpSession(sessionManagerHolder.getSessionManager(), originSessionId);
            // 创建HttpContext对象
            HttpContext context = new FullHttpContext(httpInfoRequest, httpSession, cookieManager, connectSession);
            // 执行中间件
            Object result = chain.invoke(context);
            HttpResponse response = converter.convertor(result);
            // 处理Cookie
            cookieManager.setCookieForResponse(response);
            // 处理session
            if (httpSession.sessionId() != null && (!Objects.equals(httpSession.sessionId(), originSessionId) || httpSession.pathIsUpdate())) {
                response.headers().add("Set-Cookie", "JSESSIONID=" + httpSession.sessionId() + "; Path="+ httpSession.getPath() +"; HttpOnly");
            }
            return response;
        } finally {
            releaseFiles(httpInfoRequest);
            if (httpSession != null) {
                httpSession.expireAt();
            }
        }
    }


    /**
     * 释放文件上传对象
     * @param httpInfoRequest 请求对象
     */
    private void releaseFiles(HttpInfoRequest httpInfoRequest) {
        if (httpInfoRequest == null) {
            return;
        }
        Map<String, List<FileUpload>> fileUploads = httpInfoRequest.getContent().getFormFiles();
        if (fileUploads != null) {
            for (List<FileUpload> fileUploadList : fileUploads.values()) {
                for (FileUpload fileUpload : fileUploadList) {
                    if (fileUpload != null && fileUpload.refCnt() > 0) {
                        fileUpload.release();
                    }
                }
            }
        }
    }
}
