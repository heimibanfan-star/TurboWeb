package org.turbo.web.core.http.execetor.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
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
import org.turbo.web.core.http.sse.SSESession;
import org.turbo.web.exception.TurboExceptionHandlerException;
import org.turbo.web.exception.TurboReactiveException;
import org.turbo.web.exception.TurboSerializableException;
import org.turbo.web.utils.common.BeanUtils;
import org.turbo.web.utils.http.HttpInfoRequestPackageUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.InvocationTargetException;
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
    public void execute(FullHttpRequest request, Promise<HttpResponse> promise, SSESession session) {
        HttpInfoResponse response = new HttpInfoResponse(request.protocolVersion(), HttpResponseStatus.OK);
        Mono<HttpResponse> responseMono = doExecute(request, response, session);
        responseMono
            .subscribeOn(Schedulers.fromExecutor(SERVICE_POOL))
            .subscribe(
            promise::setSuccess,
            (err) -> {
                try {
                    handleException(request, err)
                        .subscribeOn(Schedulers.fromExecutor(SERVICE_POOL))
                        .subscribe(promise::setSuccess, promise::setFailure);
                } catch (Throwable cause) {
                    promise.setFailure(cause);
                }
            });
    }

    private Mono<HttpResponse> doExecute(FullHttpRequest request, HttpInfoResponse response, SSESession session) {
        try {
            // 封装请求对象
            HttpInfoRequest infoRequest = HttpInfoRequestPackageUtils.packageRequest(request);
            // 创建上下文对象
            HttpContext context = new HttpContext(infoRequest, response, sentinelMiddleware, session);
            // 执行链式结构
            Object result = context.doNext();
            // 判断返回结果
            if (result instanceof Mono<?> mono) {
                return mono.map(o -> handleResponse(response, o));
            } else {
                return Mono.error(new TurboReactiveException("Turbo仅支持Mono类型的反应式对象"));
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
    private HttpResponse handleResponse(HttpInfoResponse response, Object result) {
        if (result instanceof HttpResponse httpResponse) {
            if (response != httpResponse) {
                response.release();
            }
            return httpResponse;
        } else if (result instanceof String string) {
            response.setContent(string);
            response.setContentType("text/plain;charset=" + config.getCharset().name());
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

    /**
     * 调度异常处理器
     *
     * @param request 请求对象
     * @param e 异常对象
     * @return 响应结果
     */
    private Mono<HttpResponse> handleException(FullHttpRequest request, Throwable e) {
        // 获取异常处理器的定义信息
        ExceptionHandlerDefinition definition = matchExceptionHandlerDefinition(e);
        // 调用异常处理器
        try {
            HttpInfoResponse response = new HttpInfoResponse(request.protocolVersion(), definition.getHttpResponseStatus());
            Object result = doHandleException(definition, e);
            // 判断结果的类型
            if (result instanceof Mono<?> mono) {
                return mono.map(o -> handleResponse(response, o));
            } else {
                return Mono.error(new TurboReactiveException("反应式中异常处理器仅支持Mono"));
            }
        } catch (IllegalAccessException | InvocationTargetException ex) {
            log.error("异常处理器中调用方法时出现错误" + e);
            throw new TurboExceptionHandlerException("异常处理器中出现错误：" + ex.getMessage());
        }
    }
}
