package top.turboweb.http.router.parameter;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.core.cookie.HttpCookies;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.core.session.HttpSession;

import java.lang.reflect.Parameter;
import java.nio.channels.Channel;

/**
 * 内部属性参数解析器
 */
public class InternTypeParameterParser extends AbstractParameterParser{

    // HttpContext自动绑定
    private static final ParameterParser.Binder HTTP_CONTEXT_BINDER = (ctx, pathParams, beanValidator) -> ctx;
    // HttpSession自动绑定
    private static final ParameterParser.Binder HTTP_SESSION_BINDER = (ctx, pathParams, beanValidator) -> ctx.httpSession();
    // HttpCookies自动绑定
    private static final ParameterParser.Binder HTTP_COOKIES_BINDER = (ctx, pathParams, beanValidator) -> ctx.cookies();
    // SseResponse自动绑定
    private static final ParameterParser.Binder SSE_RESPONSE_BINDER = (ctx, pathParams, beanValidator) -> ctx.createSseResponse();
    // SseEmitter自动绑定
    private static final ParameterParser.Binder SSE_EMITTER_BINDER = (ctx, pathParams, beanValidator) -> ctx.createSseEmitter();
    // 连接会话绑定
    private static final ParameterParser.Binder CONNECT_SESSION_BINDER = (ctx, pathParams, beanValidator) -> ctx.getConnectSession();
    // 连接通道自动绑定
    private static final ParameterParser.Binder CHANNEL_BINDER = (ctx, pathParams, beanValidator) -> ((InternalConnectSession) ctx.getConnectSession()).getChannel();

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
        if (ConnectSession.class == type) {
            return CONNECT_SESSION_BINDER;
        }
        if (Channel.class == type) {
            return CHANNEL_BINDER;
        }
        return null;
    }
}
