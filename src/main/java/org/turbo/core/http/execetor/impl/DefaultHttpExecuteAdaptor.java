package org.turbo.core.http.execetor.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.constants.FontColors;
import org.turbo.core.http.context.HttpContext;
import org.turbo.core.http.execetor.HttpDispatcher;
import org.turbo.core.http.execetor.HttpExecuteAdaptor;
import org.turbo.core.http.handler.ExceptionHandlerDefinition;
import org.turbo.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.core.http.middleware.HttpDispatcherExecuteMiddleware;
import org.turbo.core.http.middleware.Middleware;
import org.turbo.core.http.middleware.SentinelMiddleware;
import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.core.http.response.HttpInfoResponse;
import org.turbo.exception.TurboExceptionHandlerInvokeExceprion;
import org.turbo.exception.TurboSerializableException;
import org.turbo.utils.common.BeanUtils;
import org.turbo.utils.http.HttpInfoRequestPackageUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认http 处理适配器
 */
public class DefaultHttpExecuteAdaptor implements HttpExecuteAdaptor {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpExecuteAdaptor.class);
    private final Middleware sentinelMiddleware = new SentinelMiddleware();
    private final ExceptionHandlerMatcher exceptionHandlerMatcher;
    private final Map<String, String> colors = new ConcurrentHashMap<>(4);
    private final ObjectMapper objectMapper = BeanUtils.getObjectMapper();
    private boolean showRequestLog = true;

    {
        colors.put("GET", FontColors.GREEN);
        colors.put("POST", FontColors.YELLOW);
        colors.put("PUT", FontColors.BLUE);
        colors.put("DELETE", FontColors.RED);
    }

    public DefaultHttpExecuteAdaptor(HttpDispatcher httpDispatcher, List<Middleware> middlewares, ExceptionHandlerMatcher exceptionHandlerMatcher) {
        initMiddleware(httpDispatcher, middlewares);
        this.exceptionHandlerMatcher = exceptionHandlerMatcher;
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
    }

    @Override
    public HttpInfoResponse execute(FullHttpRequest request) {
        if (showRequestLog) {
            long startTime = System.currentTimeMillis();
            try {
                return doExecutor(request);
            } finally {
                log(request, System.currentTimeMillis() - startTime);
            }
        } else {
            return doExecutor(request);
        }
    }

    private HttpInfoResponse doExecutor(FullHttpRequest request) {
        HttpInfoRequest httpInfoRequest = HttpInfoRequestPackageUtils.packageRequest(request);
        try {
            // 创建响应对象
            HttpInfoResponse response = new HttpInfoResponse(request.protocolVersion(), HttpResponseStatus.OK);
            HttpContext context = new HttpContext(httpInfoRequest, response, sentinelMiddleware);
            Object result = context.doNext();
            // 判断是否写入内容
            if (context.isWrite()) {
                return response;
            }
            if (result == null) {
                response.setStatus(HttpResponseStatus.NO_CONTENT);
                return response;
            }
            if (result instanceof String) {
                response.setContent((String) result);
            } else {
                try {
                    response.setContentType(objectMapper.writeValueAsString(result));
                } catch (JsonProcessingException e) {
                    log.error("序列化失败:", e);
                    throw new TurboSerializableException(e.getMessage());
                }
            }
            return response;
        } catch (Throwable e) {
            // 获取异常处理器的定义信息
            ExceptionHandlerDefinition definition = exceptionHandlerMatcher.match(e.getClass());
            // 判断是否获取到
            if (definition == null) {
                throw e;
            }
            // 获取异常处理器实例
            Object handler = exceptionHandlerMatcher.getInstance(definition.getHandlerClass());
            if (handler == null) {
                throw new TurboExceptionHandlerInvokeExceprion("未获取到异常处理器实例");
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
                throw new TurboExceptionHandlerInvokeExceprion("异常处理器中出现错误：" + ex.getMessage());
            } catch (JsonProcessingException ex) {
                log.error("序列化失败", ex);
                throw new TurboSerializableException(ex.getMessage());
            }
        }
    }

    @Override
    public void setShowRequestLog(boolean showRequestLog) {
        this.showRequestLog = showRequestLog;
    }

    private void log(FullHttpRequest request, long ms) {
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
}
