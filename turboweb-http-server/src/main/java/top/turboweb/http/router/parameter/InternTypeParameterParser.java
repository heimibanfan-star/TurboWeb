package top.turboweb.http.router.parameter;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.connection.HttpConnection;
import top.turboweb.http.core.connect.InternalHttpConnection;
import top.turboweb.http.cookie.HttpCookies;
import top.turboweb.http.core.response.InternalSseEmitter;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.session.HttpSession;

import java.lang.reflect.Parameter;
import java.nio.channels.Channel;

/**
 * 内部属性参数解析器
 */
public class InternTypeParameterParser extends AbstractParameterParser{

    /** SSE 消息缓存容量，绑定器就地构造发射器的统一取值 */
    private static final int SSE_MAX_MESSAGE_CACHE = 32;

    // HttpContext自动绑定
    private static final ParameterParser.Binder HTTP_CONTEXT_BINDER = (ctx, requestParameter, beanValidator, jsonSerializer) -> ctx;
    // HttpSession自动绑定
    private static final ParameterParser.Binder HTTP_SESSION_BINDER = (ctx, requestParameter, beanValidator, jsonSerializer) -> ctx.httpSession();
    // HttpCookies自动绑定
    private static final ParameterParser.Binder HTTP_COOKIES_BINDER = (ctx, requestParameter, beanValidator, jsonSerializer) -> ctx.cookies();
    // SseResponse自动绑定:绑定点经连接会话与路由侧序列化器就地构造
    private static final ParameterParser.Binder SSE_RESPONSE_BINDER = (ctx, requestParameter, beanValidator, jsonSerializer) ->
            new SseResponse(HttpResponseStatus.OK, new DefaultHttpHeaders(), ctx.getConnectSession(), jsonSerializer);
    // SseEmitter自动绑定:绑定点经连接会话就地构造,缓存容量取统一值32
    private static final ParameterParser.Binder SSE_EMITTER_BINDER = (ctx, requestParameter, beanValidator, jsonSerializer) ->
            new InternalSseEmitter(ctx.getConnectSession(), SSE_MAX_MESSAGE_CACHE);
    // 连接会话绑定
    private static final ParameterParser.Binder CONNECT_SESSION_BINDER = (ctx, requestParameter, beanValidator, jsonSerializer) -> ctx.getConnectSession();
    // 连接通道自动绑定
    private static final ParameterParser.Binder CHANNEL_BINDER = (ctx, requestParameter, beanValidator, jsonSerializer) -> ((InternalHttpConnection) ctx.getConnectSession()).getChannel();

    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        // 获取参数类型
        Class<?> type = parameter.getType();
        if (HttpContext.class == type) {
            return HTTP_CONTEXT_BINDER;
        }
        if (HttpSession.class == type) {
            return HTTP_SESSION_BINDER;
        }
        if (HttpCookies.class == type) {
            return HTTP_COOKIES_BINDER;
        }
        if (SseResponse.class == type) {
            return SSE_RESPONSE_BINDER;
        }
        if (SseEmitter.class == type) {
            return SSE_EMITTER_BINDER;
        }
        if (HttpConnection.class == type) {
            return CONNECT_SESSION_BINDER;
        }
        if (Channel.class == type) {
            return CHANNEL_BINDER;
        }
        return null;
    }
}
