package top.turboweb.http.scheduler.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.cookie.Cookies;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.http.session.DefaultHttpSession;
import top.turboweb.http.session.HttpSession;
import top.turboweb.http.session.SessionManagerHolder;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.commons.exception.TurboReactiveException;
import top.turboweb.commons.exception.TurboSerializableException;
import top.turboweb.commons.utils.base.BeanUtils;
import top.turboweb.http.handler.ExceptionHandlerSchedulerHelper;
import top.turboweb.http.request.HttpInfoRequestPackageHelper;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ForkJoinPool;

/**
 * 反应式的http请求调度器
 * 该调度器还为完善，有部分bug, 待完善
 * 目前不推荐使用该调度器
 */
@Deprecated
public class ReactiveHttpScheduler extends AbstractHttpScheduler {

    private final ForkJoinPool SERVICE_POOL;
    private final ObjectMapper objectMapper = BeanUtils.getObjectMapper();
    private final Charset charset = StandardCharsets.UTF_8;

    public ReactiveHttpScheduler(
        SessionManagerHolder sessionManagerHolder,
        Middleware chain,
        ExceptionHandlerMatcher exceptionHandlerMatcher,
        int forkJoinNum
    ) {
        super(
                sessionManagerHolder,
            chain,
            exceptionHandlerMatcher,
            ReactiveHttpScheduler.class
        );
        SERVICE_POOL = new ForkJoinPool(forkJoinNum);
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
                    // 初始化session
                    Cookies cookies = httpInfoRequest.getCookies();
                    String originSessionId = cookies.getCookie("JSESSIONID");
                    HttpSession httpSession = new DefaultHttpSession(sessionManagerHolder.getSessionManager(), originSessionId);
                    // 创建上下文对象
                    HttpContext context = new FullHttpContext(httpInfoRequest, httpSession, response, session);
                    // 执行链式结构
                    Object result = sentinelMiddleware.invoke(context);
                    // 判断返回结果
                    if (result instanceof Mono<?> mono) {
                        return mono.map(o -> {
                                    HttpResponse resultResponse = handleResponse(response, o);
                                    handleSessionAfterRequest(httpSession, resultResponse, originSessionId);
                                    return resultResponse;
                                })
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
            response.setContentType("text/plain;charset=" + charset.name());
            return response;
        } else {
            try {
                String s = objectMapper.writeValueAsString(result);
                response.setContent(s);
                response.setContentType("application/json;charset=" + charset.name());
                return response;
            } catch (JsonProcessingException e) {
                throw new TurboSerializableException("序列化失败");
            }
        }
    }
}
