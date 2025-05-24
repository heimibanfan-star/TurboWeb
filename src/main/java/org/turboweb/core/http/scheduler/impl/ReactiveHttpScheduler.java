package org.turboweb.core.http.scheduler.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.turboweb.core.config.ServerParamConfig;
import org.turboweb.core.http.context.FullHttpContext;
import org.turboweb.core.http.context.HttpContext;
import org.turboweb.core.http.handler.ExceptionHandlerMatcher;
import org.turboweb.core.http.middleware.Middleware;
import org.turboweb.core.http.request.HttpInfoRequest;
import org.turboweb.core.http.response.HttpInfoResponse;
import org.turboweb.core.http.session.SessionManagerProxy;
import org.turboweb.core.connect.ConnectSession;
import org.turboweb.commons.exception.TurboReactiveException;
import org.turboweb.commons.exception.TurboSerializableException;
import org.turboweb.commons.utils.common.BeanUtils;
import org.turboweb.core.http.handler.ExceptionHandlerSchedulerHelper;
import org.turboweb.core.http.request.HttpInfoRequestPackageHelper;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ForkJoinPool;

/**
 * 反应式的http请求调度器
 */
public class ReactiveHttpScheduler extends AbstractHttpScheduler {

    private final ForkJoinPool SERVICE_POOL;
    private final ObjectMapper objectMapper = BeanUtils.getObjectMapper();

    public ReactiveHttpScheduler(
        SessionManagerProxy sessionManagerProxy,
        Middleware chain,
        ExceptionHandlerMatcher exceptionHandlerMatcher,
        ServerParamConfig config
    ) {
        super(
            sessionManagerProxy,
            chain,
            exceptionHandlerMatcher,
            config,
            ReactiveHttpScheduler.class
        );
        SERVICE_POOL = new ForkJoinPool(config.getReactiveServiceThreadNum());
    }

    @Override
    public void execute(FullHttpRequest request, ConnectSession session) {
        long startTime = System.nanoTime();
        HttpInfoResponse response = new HttpInfoResponse(request.protocolVersion(), HttpResponseStatus.OK);
        Mono<HttpResponse> responseMono = doExecute(request, response, session);
        responseMono
            .subscribeOn(Schedulers.fromExecutor(SERVICE_POOL))
            .doFinally((signalType) -> {
                request.release();
            })
            .subscribe(
                (res) -> {
                    writeResponse(session, request, res, startTime);
                },
                (err) -> {
                    ExceptionHandlerSchedulerHelper.doHandleForReactiveScheduler(exceptionHandlerMatcher, response, err)
                        .subscribeOn(Schedulers.fromExecutor(SERVICE_POOL))
                        .subscribe(res -> {
                            writeResponse(session, request, res, startTime);
                        });
                });
    }

    private Mono<HttpResponse> doExecute(FullHttpRequest fullHttpRequest, HttpInfoResponse response, ConnectSession session) {
        return Mono.just(fullHttpRequest)
            .flatMap(request -> {
                HttpInfoRequest httpInfoRequestForErrorRelease = null;
                try {
                    // 封装请求对象
                    HttpInfoRequest httpInfoRequest = HttpInfoRequestPackageHelper.packageRequest(request);
                    httpInfoRequestForErrorRelease = httpInfoRequest;
                    // 创建上下文对象
                    HttpContext context = new FullHttpContext(httpInfoRequest, response, session);
                    // 执行链式结构
                    Object result = sentinelMiddleware.invoke(context);
                    // 判断返回结果
                    if (result instanceof Mono<?> mono) {
                        return mono.map(o -> handleResponse(response, o))
                            .doFinally(signalType -> {
                                // 释放文件上传数据
                                releaseFileUploads(httpInfoRequest);
                            });
                    } else {
                        return Mono.just(new TurboReactiveException("TurboWeb仅支持Mono类型的反应式对象"))
                            .doFinally(signalType -> {
                                // 释放文件数据
                                releaseFileUploads(httpInfoRequest);
                            })
                            .flatMap(Mono::error);
                    }
                } catch (Exception e) {
                    try {
                        releaseFileUploads(httpInfoRequestForErrorRelease);
                    } catch (Exception ex) {
                        // 忽略异常
                    }
                    return Mono.error(e);
                }
            });
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
}
