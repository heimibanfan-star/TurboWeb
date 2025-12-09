package top.turboweb.http.processor;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.hc.core5.http.ContentType;
import top.turboweb.commons.lock.Locks;
import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.respmeta.ResponseMetaGetter;
import top.turboweb.http.cookie.DefaultHttpCookieManager;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.processor.convertor.HttpResponseConverter;
import top.turboweb.http.response.HttpResult;
import top.turboweb.http.session.BackHoleSessionManager;
import top.turboweb.http.session.DefaultHttpSession;
import top.turboweb.http.session.HttpSession;
import top.turboweb.http.session.SessionManagerHolder;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * 内核处理器：中间件执行处理器。
 * <p>
 * 该处理器负责在 HTTP 请求处理链中执行中间件逻辑。
 * 它完成以下功能：
 * <ul>
 *     <li>初始化 {@link HttpContext}，封装请求、Session、Cookie 等信息</li>
 *     <li>执行中间件链 {@link Middleware}</li>
 *     <li>处理返回结果，将对象转换为 {@link HttpResponse}</li>
 *     <li>处理响应中的 Cookie 和 Session 信息</li>
 *     <li>释放上下文资源，确保请求结束后清理会话和内存</li>
 * </ul>
 * </p>
 * <p>
 * 内部会根据 SessionManager 类型决定是否加读锁：
 * <ul>
 *     <li>如果使用 {@link BackHoleSessionManager}，则无需加锁</li>
 *     <li>否则对 Session 访问加读锁，确保并发安全</li>
 * </ul>
 * </p>
 */
public class MiddlewareInvokeProcessor extends Processor{

    /** 中间件链 */
    private final Middleware chain;

    /** 会话管理器持有者 */
    private final SessionManagerHolder sessionManagerHolder;

    /** HTTP 响应转换器 */
    private final HttpResponseConverter converter;

    /** JSON 序列化器 */
    private final JsonSerializer jsonSerializer;

    /**
     * 构造方法
     *
     * @param chain 中间件链
     * @param sessionManagerHolder 会话管理器持有者
     * @param converter HTTP 响应转换器
     * @param jsonSerializer JSON 序列化器
     */
    public MiddlewareInvokeProcessor(
            Middleware chain,
            SessionManagerHolder sessionManagerHolder,
            HttpResponseConverter converter,
            JsonSerializer jsonSerializer
    ) {
        this.chain = chain;
        this.sessionManagerHolder = sessionManagerHolder;
        this.converter = converter;
        this.jsonSerializer = jsonSerializer;
    }

    /**
     * 执行请求。
     * <p>
     * 根据 SessionManager 类型判断是否需要加锁，然后执行中间件链。
     * </p>
     *
     * @param fullHttpRequest HTTP 请求对象
     * @param connectSession 连接会话对象
     * @return HTTP 响应对象
     */
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
     * 执行中间件链逻辑。
     * <p>
     * 初始化 HttpContext，执行中间件链，将结果转换为 HttpResponse，
     * 并处理 Cookie 与 Session，最终释放上下文资源。
     * </p>
     *
     * @param fullHttpRequest HTTP 请求对象
     * @param connectSession 连接会话对象
     * @return HTTP 响应对象
     */
    private HttpResponse executeMiddleware(FullHttpRequest fullHttpRequest, ConnectSession connectSession) {
        HttpContext context = null;
        try {
            // 初始化Cookie
            HttpCookieManager cookieManager = new DefaultHttpCookieManager(fullHttpRequest.headers());
            // 初始化session
            String originSessionId = cookieManager.getCookie("JSESSIONID");
            HttpSession httpSession = new DefaultHttpSession(sessionManagerHolder.getSessionManager(), originSessionId);
            // 创建HttpContext对象
            context = new FullHttpContext(fullHttpRequest, httpSession, cookieManager, connectSession, jsonSerializer);
            // 执行中间件
            Object result = chain.invoke(context);
            boolean shouldSetMeta = (!(result instanceof HttpResponse) && !(result instanceof HttpResult<?>));
            HttpResponse response = converter.convertor(result);
            // 判断是否需要设置响应的元信息
            if (shouldSetMeta) {
                setRespMeta(response, context);
            }
            // 处理Cookie
            cookieManager.setCookieForResponse(response);
            // 处理session
            if (httpSession.sessionId() != null && (!Objects.equals(httpSession.sessionId(), originSessionId) || httpSession.pathIsUpdate())) {
                response.headers().add("Set-Cookie", "JSESSIONID=" + httpSession.sessionId() + "; Path="+ httpSession.getPath() +"; HttpOnly");
            }
            return response;
        } finally {
            if (context != null) {
                context.release();
                context.httpSession().expireAt();
            }
        }
    }

    /**
     * 设置响应元信息。
     * <p>
     * 根据 {@link ResponseMetaGetter} 的返回值，设置响应状态码和响应类型。
     * </p>
     *
     * @param response HTTP 响应对象
     * @param context HTTP 上下文对象
     */
    private void setRespMeta(HttpResponse response, HttpContext context) {
        ResponseMetaGetter metaGetter = (ResponseMetaGetter) context.getResponseMeta();
        HttpResponseStatus status = metaGetter.getStatus();
        // 判断是否需要设置响应状态码
        if (status != null) {
            response.setStatus(status);
        }
        // 判断是否需要设置响应类型
        if (metaGetter.getContentType() != null) {
            ContentType contentType = metaGetter.getContentType();
            Charset charset = contentType.getCharset() != null? contentType.getCharset() : Charset.defaultCharset();
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType() + "; charset=" + charset.name());
        }
    }


}
