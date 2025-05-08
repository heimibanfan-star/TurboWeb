package org.turbo.web.core.http.scheduler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.constants.FontColors;
import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.scheduler.HttpScheduler;
import org.turbo.web.core.http.handler.ExceptionHandlerDefinition;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.request.HttpInfoRequest;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.session.Session;
import org.turbo.web.core.http.session.SessionManagerProxy;
import org.turbo.web.exception.TurboExceptionHandlerException;
import org.turbo.web.exception.TurboMethodInvokeThrowable;
import org.turbo.web.exception.TurboNotCatchException;
import org.turbo.web.utils.common.BeanUtils;
import org.turbo.web.utils.common.RandomUtils;

import java.lang.invoke.MethodHandle;
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
    protected boolean showRequestLog = true;
    protected final ServerParamConfig config;
    protected final ObjectMapper objectMapper = BeanUtils.getObjectMapper();

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
        ServerParamConfig config,
        Class<?> subClass
    ) {
        this.log = LoggerFactory.getLogger(subClass);
        this.exceptionHandlerMatcher = exceptionHandlerMatcher;
        this.sessionManagerProxy = sessionManagerProxy;
        this.config = config;
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
                    fileUpload.release();
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
    protected void log(FullHttpRequest request, long time) {
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
     * 执行异常处理器
     *
     * @param definition 异常定义信息
     * @param e 异常对象
     * @return 异常处理器的执行结果
     */
    protected Object doHandleException(ExceptionHandlerDefinition definition, Throwable e) throws TurboMethodInvokeThrowable {
        MethodHandle methodHandler = definition.getMethodHandler();
        try {
            return methodHandler.invoke(e);
        } catch (Throwable ex) {
            throw new TurboMethodInvokeThrowable(ex);
        }
    }

    /**
     * 匹配异常处理器的定义信息
     *
     * @param e 异常对象
     * @return 异常处理器的定义信息
     */
    protected ExceptionHandlerDefinition matchExceptionHandlerDefinition(Throwable e) {
        // 获取异常处理器的定义信息
        ExceptionHandlerDefinition definition = exceptionHandlerMatcher.match(e.getClass());
        // 判断是否获取到
        if (definition == null) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new TurboNotCatchException(e.getMessage(), e);
        }
        return definition;
    }
}
