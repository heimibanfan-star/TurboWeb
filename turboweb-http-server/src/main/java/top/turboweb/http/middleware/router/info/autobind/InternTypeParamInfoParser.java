package top.turboweb.http.middleware.router.info.autobind;

import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.SseResponse;
import top.turboweb.http.session.HttpSession;

import java.lang.reflect.Parameter;
import java.nio.channels.Channel;

/**
 * 内部属性参数解析器
 */
public class InternTypeParamInfoParser implements ParameterInfoParser{

    // HttpContext自动绑定
    private static final ParameterBinder HTTP_CONTEXT_BINDER = ctx -> ctx;
    // HttpSession自动绑定
    private static final ParameterBinder HTTP_SESSION_BINDER = HttpContext::httpSession;
    // CookieManager自动绑定
    private static final ParameterBinder COOKIE_MANAGER_BINDER = HttpContext::cookie;
    // SseResponse自动绑定
    private static final ParameterBinder SSE_RESPONSE_BINDER = HttpContext::createSseResponse;
    // SseEmitter自动绑定
    private static final ParameterBinder SSE_EMITTER_BINDER = HttpContext::createSseEmitter;
    // 连接会话绑定
    private static final ParameterBinder CONNECT_SESSION_BINDER = HttpContext::getConnectSession;
    // 连接通道自动绑定
    private static final ParameterBinder CHANNEL_BINDER = ctx -> ((InternalConnectSession) ctx.getConnectSession()).getChannel() ;

    @Override
    public ParameterBinder parse(Parameter parameter) {
        // 获取参数类型
        Class<?> type = parameter.getType();
        if (HttpContext.class == type) {
            return HTTP_CONTEXT_BINDER;
        }
        if (HttpSession.class == type) {
            return HTTP_SESSION_BINDER;
        }
        if (HttpCookieManager.class == type) {
            return COOKIE_MANAGER_BINDER;
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
