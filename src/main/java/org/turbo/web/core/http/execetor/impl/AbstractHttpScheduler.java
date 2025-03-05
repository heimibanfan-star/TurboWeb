package org.turbo.web.core.http.execetor.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.constants.FontColors;
import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.execetor.HttpDispatcher;
import org.turbo.web.core.http.execetor.HttpScheduler;
import org.turbo.web.core.http.handler.ExceptionHandlerDefinition;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.HttpDispatcherExecuteMiddleware;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.middleware.SentinelMiddleware;
import org.turbo.web.core.http.middleware.aware.CharsetAware;
import org.turbo.web.core.http.middleware.aware.ExceptionHandlerMatcherAware;
import org.turbo.web.core.http.middleware.aware.MainClassAware;
import org.turbo.web.core.http.middleware.aware.SessionManagerProxyAware;
import org.turbo.web.core.http.request.HttpInfoRequest;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.session.Session;
import org.turbo.web.core.http.session.SessionManagerProxy;
import org.turbo.web.exception.TurboExceptionHandlerException;
import org.turbo.web.exception.TurboNotCatchException;
import org.turbo.web.utils.common.BeanUtils;
import org.turbo.web.utils.common.RandomUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽象http请求调度器
 */
public abstract class AbstractHttpScheduler implements HttpScheduler {

    protected final Logger log;
    protected final Middleware sentinelMiddleware = new SentinelMiddleware();
    protected final ExceptionHandlerMatcher exceptionHandlerMatcher;
    private final Map<String, String> colors = new ConcurrentHashMap<>(4);
    private final SessionManagerProxy sessionManagerProxy;
    protected boolean showRequestLog = true;
    private final Class<?> mainClass;
    protected final ServerParamConfig config;
    protected final ObjectMapper objectMapper = BeanUtils.getObjectMapper();

    {
        colors.put("GET", FontColors.GREEN);
        colors.put("POST", FontColors.YELLOW);
        colors.put("PUT", FontColors.BLUE);
        colors.put("DELETE", FontColors.RED);
    }

    public AbstractHttpScheduler(
        HttpDispatcher httpDispatcher,
        SessionManagerProxy sessionManagerProxy,
        Class<?> mainClass,
        List<Middleware> middlewares,
        ExceptionHandlerMatcher exceptionHandlerMatcher,
        ServerParamConfig config,
        Class<?> clazz
    ) {
        this.log = LoggerFactory.getLogger(clazz);
        this.exceptionHandlerMatcher = exceptionHandlerMatcher;
        this.sessionManagerProxy = sessionManagerProxy;
        this.mainClass = mainClass;
        this.config = config;
        // 初始化中间件链
        initMiddleware(httpDispatcher, middlewares);
        // 对中间件进行依赖注入
        initMiddlewareChainForAware();
        // 调用中间件的初始化方法
        doMiddlewareChainInit();
    }

    /**
     * 初始化中间件
     *
     * @param httpDispatcher http请求分发器
     * @param middlewares 中间件
     */
    private void initMiddleware(HttpDispatcher httpDispatcher, List<Middleware> middlewares) {
        Middleware ptr = sentinelMiddleware;
        for (Middleware middleware : middlewares) {
            ptr.setNext(middleware);
            ptr = middleware;
        }
        ptr.setNext(new HttpDispatcherExecuteMiddleware(httpDispatcher));
        log.debug("中间件链组装完成");
    }

    /**
     * 对中间件进行依赖注入
     */
    private void initMiddlewareChainForAware() {
        Middleware ptr = sentinelMiddleware;
        while (ptr != null) {
            // 判断是否实现Aware
            if (ptr instanceof SessionManagerProxyAware aware) {
                aware.setSessionManagerProxy(sessionManagerProxy);
            }
            if (ptr instanceof ExceptionHandlerMatcherAware aware) {
                aware.setExceptionHandlerMatcher(exceptionHandlerMatcher);
            }
            if (ptr instanceof MainClassAware aware) {
                aware.setMainClass(mainClass);
            }
            if (ptr instanceof CharsetAware aware) {
                aware.setCharset(config.getCharset());
            }
            ptr = ptr.getNext();
        }
        log.debug("中间件依赖注入完成");
    }

    /**
     * 执行中间件的初始化方法
     */
    private void doMiddlewareChainInit() {
        Middleware ptr = sentinelMiddleware;
        while (ptr != null) {
            ptr.init(this.sentinelMiddleware);
            ptr = ptr.getNext();
        }
        log.debug("中间件初始化方法执行完成");
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
     * @param ms 执行耗时
     */
    protected void log(FullHttpRequest request, long ms) {
        String method = request.method().name();
        if (!colors.containsKey(method)) {
            return;
        }
        String color = colors.get(method);
        String uri = request.uri();
        if (ms > 0) {
            System.out.println(color + "%s  %s  耗时:%sms".formatted(method, uri, ms));
        } else {
            System.out.println(color + "%s  %s  耗时: <1ms".formatted(method, uri));
        }
        System.out.print(FontColors.BLACK);
    }

    /**
     * 执行异常处理器
     *
     * @param definition 异常定义信息
     * @param e 异常对象
     * @return 异常处理器的执行结果
     */
    protected Object doHandleException(ExceptionHandlerDefinition definition, Throwable e) throws InvocationTargetException, IllegalAccessException {
        // 获取异常处理器实例
        Object handler = exceptionHandlerMatcher.getInstance(definition.getHandlerClass());
        if (handler == null) {
            throw new TurboExceptionHandlerException("未获取到异常处理器实例");
        }
        Method method = definition.getMethod();
        return method.invoke(handler, e);
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
