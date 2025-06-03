package top.turboweb.http.scheduler.impl;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.constants.FontColors;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.response.handler.DefaultHttpResponseHandler;
import top.turboweb.http.response.handler.HttpResponseHandler;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.http.session.HttpSession;
import top.turboweb.http.session.SessionManagerHolder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽象http请求调度器
 */
public abstract class AbstractHttpScheduler implements HttpScheduler {

    protected final Logger log;
    protected final Middleware sentinelMiddleware;
    protected final ExceptionHandlerMatcher exceptionHandlerMatcher;
    private final Map<String, String> colors = new ConcurrentHashMap<>(4);
    protected final SessionManagerHolder sessionManagerHolder;
    private final HttpResponseHandler httpResponseHandler = new DefaultHttpResponseHandler();
    protected boolean showRequestLog = true;

    {
        colors.put("GET", FontColors.GREEN);
        colors.put("POST", FontColors.YELLOW);
        colors.put("PUT", FontColors.BLUE);
        colors.put("DELETE", FontColors.RED);
        colors.put("PATCH", FontColors.MAGENTA);
    }

    public AbstractHttpScheduler(
        SessionManagerHolder sessionManagerHolder,
        Middleware chain,
        ExceptionHandlerMatcher exceptionHandlerMatcher,
        Class<?> subClass
    ) {
        this.log = LoggerFactory.getLogger(subClass);
        this.exceptionHandlerMatcher = exceptionHandlerMatcher;
        this.sessionManagerHolder = sessionManagerHolder;
        this.sentinelMiddleware = chain;
    }

    /**
     * 释放文件上传
     *
     * @param request 请求信息
     */
    protected void releaseFileUploads(HttpInfoRequest request) {
        if (request == null) {
            return;
        }
        Map<String, List<FileUpload>> fileUploads = request.getContent().getFormFiles();
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

    /**
     * 在请求结束后对session进行处理
     *
     * @param session session会话对象
     * @param response 响应对象
     * @param originSessionId sessionId
     */
    protected void handleSessionAfterRequest(HttpSession session, HttpResponse response, String originSessionId) {
        try {
            if (session.sessionId() == null) {
                return;
            }
            // 判断session是否需要重新响应
            if (!Objects.equals(session.sessionId(), originSessionId) || session.pathIsUpdate()) {
                response.headers().add("Set-Cookie", "JSESSIONID=" + session.sessionId() + "; Path="+ session.getPath() +"; HttpOnly");
            }
        } finally {
            session.expireAt();
        }
    }

    @Override
    public void setShowRequestLog(boolean showRequestLog) {
        this.showRequestLog = showRequestLog;
    }

    /**
     * 打印日志
     *
     * @param request 请求对象
     * @param time 执行耗时
     */
    private void log(FullHttpRequest request, long time, boolean isSuccess) {
        String method = request.method().name();
        if (!colors.containsKey(method)) {
            return;
        }
        String color = colors.get(method);
        String uri = request.uri();
        if (time > 1000000) {
            System.out.println(color + "%s  %s  耗时:%sms  state:%s".formatted(method, uri, time / 1000000, isSuccess? "success": "fail") + FontColors.RESET);
        } else {
            System.out.println(color + "%s  %s  耗时:%sµs  state:%s".formatted(method, uri, time / 1000,  isSuccess? "success": "fail") + FontColors.RESET);
        }
    }

    /**
     * 写响应
     *
     * @param session session对象
     * @param request 请求对象
     * @param response 响应对象
     * @param startTime 开始时间
     */
    protected void writeResponse(ConnectSession session, FullHttpRequest request, HttpResponse response, long startTime) {
        ChannelFuture channelFuture = httpResponseHandler.writeHttpResponse(response, session);
        // 打印性能日志
        if (showRequestLog && channelFuture != null) {
            channelFuture.addListener(future -> {
                log(request, System.nanoTime() - startTime, future.isSuccess());
            });
        }
    }
}
