package top.turboweb.http.middleware.router.info;

import top.turboweb.anno.*;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.middleware.router.info.autobind.ParameterBinder;
import top.turboweb.http.middleware.router.info.autobind.ParameterInfoParser;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.SseResponse;
import top.turboweb.http.session.HttpSession;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自动绑定参数的路由定义信息
 */
public class AutoBindRouterDefinition implements RouterDefinition {

    private volatile ParameterBinder[] binders;
    private final List<ParameterInfoParser> parsers;
    private final MethodHandle methodHandle;
    private final Method method;
    private final ReentrantLock initLock = new ReentrantLock();

    private static final ParameterBinder DEFAULT_PARAMETER_BINDER = ctx -> null;


    public AutoBindRouterDefinition(List<ParameterInfoParser> parsers, Object instance, Method method) {
        this.parsers = parsers;
        this.method = method;
        try {
            methodHandle = MethodHandles.lookup().unreflect(method).bindTo(instance).asSpreader(Object[].class, method.getParameterCount());
        } catch (IllegalAccessException e) {
            throw new TurboRouterDefinitionCreateException(e);
        }
    }


    @Override
    public Object invoke(HttpContext ctx) throws Throwable {
        if (binders == null) {
            initLock.lock();
            try {
                if (binders == null) {
                    initBinders();
                }
            } finally {
                initLock.unlock();
            }
        }
        Object[] args = new Object[binders.length];
        for (int i = 0; i < binders.length; i++) {
            args[i] = binders[i].bindParameter(ctx);
        }
        return methodHandle.invoke(args);
    }

    /**
     * 初始化参数绑定器
     */
    private void initBinders() {
        Parameter[] parameters = method.getParameters();
        ParameterBinder[] binders = new ParameterBinder[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            ParameterBinder binder = null;
            // 依次解析所有的参数
            for (ParameterInfoParser parser : parsers) {
                binder = parser.parse(parameters[i]);
                if (binder != null) {
                    break;
                }
            }
            if (binder == null) {
                binder = DEFAULT_PARAMETER_BINDER;
            }
            binders[i] = binder;
        }
        this.binders = binders;
    }
}
