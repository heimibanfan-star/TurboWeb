package org.turbo.web.core.http.execetor.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.Promise;
import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.execetor.HttpDispatcher;
import org.turbo.web.core.http.handler.ExceptionHandlerDefinition;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.request.HttpInfoRequest;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.session.SessionManagerProxy;
import org.turbo.web.exception.TurboExceptionHandlerException;
import org.turbo.web.exception.TurboNotCatchException;
import org.turbo.web.exception.TurboReactiveException;
import org.turbo.web.exception.TurboSerializableException;
import org.turbo.web.utils.common.BeanUtils;
import org.turbo.web.utils.http.HttpInfoRequestPackageUtils;
import reactor.core.CorePublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * 反应式的http请求调度器
 */
public class ReactiveHttpScheduler extends AbstractHttpScheduler {

    private final ForkJoinPool SERVICE_POOL;
    private final ObjectMapper objectMapper = BeanUtils.getObjectMapper();

    public ReactiveHttpScheduler(
        HttpDispatcher httpDispatcher,
        SessionManagerProxy sessionManagerProxy,
        Class<?> mainClass,
        List<Middleware> middlewares,
        ExceptionHandlerMatcher exceptionHandlerMatcher,
        ServerParamConfig config
    ) {
        super(
            httpDispatcher,
            sessionManagerProxy,
            mainClass,
            middlewares,
            exceptionHandlerMatcher,
            config
        );
        SERVICE_POOL = new ForkJoinPool(config.getReactiveServiceThreadNum());
    }

    @Override
    public void execute(FullHttpRequest request, Promise<HttpInfoResponse> promise) {
        SERVICE_POOL.execute(() -> {
            HttpInfoResponse response = new HttpInfoResponse(request.protocolVersion(), HttpResponseStatus.OK);
            Mono<HttpInfoResponse> responseMono = doExecute(request, response);
            responseMono.subscribe(
                promise::setSuccess,
                (err) -> {
                    try {
                        handleException(request, err)
                            .subscribe(promise::setSuccess);
                    } catch (Throwable cause) {
                        promise.setFailure(cause);
                    }
                });
        });
    }

    private Mono<HttpInfoResponse> doExecute(FullHttpRequest request, HttpInfoResponse response) {
        try {
            // 封装请求对象
            HttpInfoRequest infoRequest = HttpInfoRequestPackageUtils.packageRequest(request);
            // 创建上下文对象
            HttpContext context = new HttpContext(infoRequest, response, sentinelMiddleware);
            // 执行链式结构
            Object result = context.doNext();
            // 判断返回结果
            if (result instanceof CorePublisher<?>) {
                if (result instanceof Mono<?> mono) {
                    return mono.map(o -> handleResponse(response, o));
                }
                ;
                if (result instanceof Flux<?> flux) {
                    return flux.collectList().map(o -> handleResponse(response, o));
                } else {
                    return Mono.error(new TurboReactiveException("返回结果必须是Mono或Flux"));
                }
            } else {
                return Mono.error(new TurboReactiveException("返回结果不是反应式对象"));
            }
        } catch (Throwable cause) {
            return Mono.error(cause);
        }
    }

    /**
     * 处理响应
     *
     * @param response 响应对象
     * @param result   结果
     * @return 响应对象
     */
    private HttpInfoResponse handleResponse(HttpInfoResponse response, Object result) {
        if (result instanceof String string) {
            response.setContent(string);
            response.setContentType("text/plain;charset=" + config.getCharset().name());
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, string.length());
            return response;
        } else {
            try {
                String s = objectMapper.writeValueAsString(result);
                response.setContent(s);
                response.setContentType("application/json;charset=" + config.getCharset().name());
                return response;
            } catch (JsonProcessingException e) {
                throw new TurboSerializableException("序列化失败");
            }
        }
    }

    private Mono<HttpInfoResponse> handleException(FullHttpRequest request, Throwable e) {
        // 获取异常处理器的定义信息
        ExceptionHandlerDefinition definition = exceptionHandlerMatcher.match(e.getClass());
        // 判断是否获取到
        if (definition == null) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new TurboNotCatchException(e.getMessage(), e);
        }
        // 获取异常处理器实例
        Object handler = exceptionHandlerMatcher.getInstance(definition.getHandlerClass());
        if (handler == null) {
            throw new TurboExceptionHandlerException("未获取到异常处理器实例");
        }
        // 调用异常处理器
        try {
            HttpInfoResponse response = new HttpInfoResponse(request.protocolVersion(), definition.getHttpResponseStatus());
            Method method = definition.getMethod();
            Object result = method.invoke(handler, e);
            // 判断结果的类型
            if (result instanceof CorePublisher<?>) {
                if (result instanceof Mono<?> mono) {
                    return mono.map(o -> handleResponse(response, o));
                }
                if (result instanceof Flux<?> flux) {
                    return flux.collectList().map(o -> handleResponse(response, o));
                } else {
                    return Mono.error(new TurboReactiveException("异常处理器返回结果必须是Mono或Flux"));
                }
            } else {
                return Mono.error(new TurboReactiveException("异常处理器返回结果必须是反应式对象"));
            }
        } catch (IllegalAccessException | InvocationTargetException ex) {
            log.error("异常处理器中调用方法时出现错误" + e);
            throw new TurboExceptionHandlerException("异常处理器中出现错误：" + ex.getMessage());
        }
    }
}
