package top.turboweb.http.scheduler.impl;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.constants.FontColors;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.adapter.DefaultHttpResponseAdapter;
import top.turboweb.http.adapter.HttpResponseAdapter;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.http.session.Session;
import top.turboweb.http.session.SessionManagerProxy;
import top.turboweb.commons.utils.base.RandomUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽象http请求调度器
 */
public abstract class AbstractHttpScheduler implements HttpScheduler {

    protected final Logger log;
    protected final Middleware sentinelMiddleware;
    protected final ExceptionHandlerMatcher exceptionHandlerMatcher;
    private final Map<String, String> colors = new ConcurrentHashMap<>(4);
    private final SessionManagerProxy sessionManagerProxy;
    private final HttpResponseAdapter httpResponseAdapter = new DefaultHttpResponseAdapter();
    protected boolean showRequestLog = true;

    {
        colors.put("GET", FontColors.GREEN);
        colors.put("POST", FontColors.YELLOW);
        colors.put("PUT", FontColors.BLUE);
        colors.put("DELETE", FontColors.RED);
        colors.put("PATCH", FontColors.MAGENTA);
    }

    public AbstractHttpScheduler(
        SessionManagerProxy sessionManagerProxy,
        Middleware chain,
        ExceptionHandlerMatcher exceptionHandlerMatcher,
        Class<?> subClass
    ) {
        this.log = LoggerFactory.getLogger(subClass);
        this.exceptionHandlerMatcher = exceptionHandlerMatcher;
        this.sessionManagerProxy = sessionManagerProxy;
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
     * 初始化session
     *
     * @param httpInfoRequest 请求信息
     */
    protected void initSession(HttpInfoRequest httpInfoRequest, String jsessionid) {
        if (jsessionid != null) {
            Session session = sessionManagerProxy.getSession(jsessionid);
            // 设置使用时间，防止被销毁
            if (session != null) {
                session.setUseTime();
            }
            httpInfoRequest.setSession(session);
        }
    }

    /**
     * 在请求结束后对session进行处理
     *
     * @param ctx 上下文对象
     * @param jsessionid sessionId
     */
    protected void handleSessionAfterRequest(HttpContext ctx, String jsessionid) {
        HttpInfoRequest request = ctx.getRequest();
        if (request.sessionIsNull()) {
            return;
        }
        if (jsessionid == null) {
            jsessionid = RandomUtils.uuidWithoutHyphen();
        }
        // 从容器中获取session
        Session session = sessionManagerProxy.getSession(jsessionid);
        if (session == null) {
            session = request.getSession();
            sessionManagerProxy.addSession(jsessionid, session);
            HttpInfoResponse response = ctx.getResponse();
            // 设置响应头
            response.headers().add("Set-Cookie", "JSESSIONID=" + jsessionid + "; Path="+ session.getPath() +"; HttpOnly");
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
    private void log(FullHttpRequest request, long time) {
        String method = request.method().name();
        if (!colors.containsKey(method)) {
            return;
        }
        String color = colors.get(method);
        String uri = request.uri();
        if (time > 1000000) {
            System.out.println(color + "%s  %s  耗时:%sms".formatted(method, uri, time / 1000000) + FontColors.RESET);
        } else {
            System.out.println(color + "%s  %s  耗时:%sµs".formatted(method, uri, time / 1000) + FontColors.RESET);
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
        ChannelFuture channelFuture = httpResponseAdapter.writeHttpResponse(response, session);
        // 打印性能日志
        if (showRequestLog && channelFuture != null) {
            channelFuture.addListener(future -> {
                log(request, System.nanoTime() - startTime);
            });
        }
    }
}
