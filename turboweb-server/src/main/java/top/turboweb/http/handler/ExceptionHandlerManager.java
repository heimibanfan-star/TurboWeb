package top.turboweb.http.handler;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.commons.exception.TurboExceptionHandlerException;
import top.turboweb.http.core.response.HttpResult;
import top.turboweb.http.exception.ExceptionHandlerException;
import top.turboweb.http.exception.RouteHandlerException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 异常处理器管理器。
 * <p>
 * 接收异常处理器实例集合，按方法签名解析认领异常处理方法，
 * 维护异常类型到 {@link ExceptionHandlerInvoker} 的不可变映射表，
 * 作为异常统一处理链路的调度入口。
 * </p>
 * <p>
 * 认领在构造期一次完成：方法须公有、未由编译器合成、有且仅有一个参数
 * 且参数类型为 {@link Throwable} 自身或其子类；不依赖注解，静态与实例方法均可。
 * 两个方法声明相同异常类型时抛出 {@link ExceptionHandlerException} 拒绝构建。
 * </p>
 * <p>
 * 异常发生时经 {@link #handle(Throwable)} 处理：自异常自身的类型起沿继承链逐级向上，
 * 优先命中绑定最近异常类型的处理器并透传其返回值；
 * 整条继承链均无匹配且异常为路由未匹配错误码的路由异常时，返回 404 状态码的 {@link HttpResult}；
 * 其余无匹配或处理器自身执行失败时，
 * 返回 500 状态码、text/html 形式并携带完整异常栈的 {@link HttpResult} 兜底响应。
 * </p>
 */
public class ExceptionHandlerManager {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ExceptionHandlerManager.class);

    private final Map<Class<? extends Throwable>, ExceptionHandlerInvoker> handlers;

    /**
     * 构造异常处理器管理器，并对处理器实例集合完成解析初始化。
     * <p>
     * 对集合内每个实例按方法签名认领异常处理方法：公有、未由编译器合成、
     * 有且仅有一个参数且参数类型为 {@link Throwable} 自身或其子类；
     * 实例方法绑定所属实例，静态方法无需绑定，其余方法一律忽略。
     * 解析产物为不可变的异常类型映射表，若出现两个相同的异常类型，
     * 抛出 {@link ExceptionHandlerException}。
     * </p>
     *
     * @param handlers 异常处理器实例集合，不能为 null
     * @throws NullPointerException          handlers 为 null
     * @throws ExceptionHandlerException     存在两个相同异常类型的处理方法
     * @throws TurboExceptionHandlerException 方法句柄不可访问
     */
    public ExceptionHandlerManager(Set<Object> handlers) {
        this.handlers = parseHandlers(Objects.requireNonNull(handlers, "handlers must not be null"));
    }

    /**
     * 匹配异常处理器处理异常。
     * <p>
     * 自异常自身的类型起沿继承链逐级向上查找，优先命中绑定最近父类异常的处理器，
     * 命中后执行并返回处理方法的返回值。
     * 若整条继承链都没有注册处理器且异常是路由未匹配异常，直接返回 404 状态码的 {@link HttpResult}；
     * 其余无匹配或处理器执行中自身抛出异常的情况，
     * 构建 500 状态码、text/html 形式并携带完整异常栈的 {@link HttpResult} 兜底响应返回，同时记录日志。
     * </p>
     *
     * @param throwable 待处理的异常
     * @return 命中的处理方法的返回值；兜底时为 404 或 500 状态码的 {@link HttpResult}
     */
    public Object handle(Throwable throwable) {
        ExceptionHandlerInvoker invoker = findInvoker(throwable);
        if (invoker == null) {
            if (isRouterNotMatch(throwable)) {
                return notFoundHandleResult(throwable);
            }
            return errorHandleResult(throwable);
        }
        try {
            return invoker.invoke(throwable);
        } catch (Throwable handlerFailure) {
            log.error("异常处理器执行失败", handlerFailure);
            return errorHandleResult(throwable);
        }
    }

    /**
     * 自异常自身的类型起沿继承链逐级向上，查找绑定最近异常类型的调用器。
     *
     * @param throwable 待处理的异常
     * @return 匹配到的调用器；整条继承链均未注册时返回 null
     */
    private ExceptionHandlerInvoker findInvoker(Throwable throwable) {
        Class<?> currentClass = throwable.getClass();
        while (Throwable.class.isAssignableFrom(currentClass)) {
            ExceptionHandlerInvoker invoker = handlers.get(currentClass);
            if (invoker != null) {
                return invoker;
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    /**
     * 判定异常是否为路由未匹配异常：类型是 {@link RouteHandlerException} 且错误码为 ROUTER_NOT_MATCH，
     * 该异常在未命中任何处理器时按 404 响应展示，其余错误码的路由异常仍走 500 兜底。
     *
     * @param throwable 待判定的异常
     * @return 是否为路由未匹配异常
     */
    private static boolean isRouterNotMatch(Throwable throwable) {
        return throwable instanceof RouteHandlerException routeHandlerException
                && RouteHandlerException.ROUTER_NOT_MATCH.equals(routeHandlerException.getCode());
    }

    /**
     * 构建路由未匹配的 404 响应：生成 HTML 错误页面展示异常消息，
     * 消息携带请求方法与被请求的 URI（来自客户端输入），经 HTML 转义后嵌入页面，
     * 其中的标记字符不会作为原始标签透传给浏览器。本情况不是服务器故障，不记录错误日志、不输出异常栈。
     *
     * @param throwable 触发 404 的路由未匹配异常
     * @return 404 状态码的 {@link HttpResult}，其数据为字符串，生成响应时按 text/html 输出
     */
    private Object notFoundHandleResult(Throwable throwable) {
        String message = throwable.getMessage() == null ? throwable.getClass().getName() : throwable.getMessage();
        String content = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="utf-8">
                    <title>TurboWeb Error</title>
                    <style>
                        body { font-family: Consolas, "Courier New", monospace; margin: 2em; }
                        h1 { color: #c0392b; }
                        .error-message { font-size: 1.1em; }
                    </style>
                </head>
                <body>
                    <h1>404 Not Found</h1>
                    <p class="error-message">异常信息：%s</p>
                </body>
                </html>
                """.formatted(htmlEscape(message));
        return HttpResult.create(404, content);
    }

    /**
     * 构建兜底响应：记录服务器异常日志，并生成完整的 HTML 错误页面，
     * 页面展示异常类型与可横向滚动的完整异常栈，栈文本经 HTML 转义后输出，
     * 消息中的标记字符不会作为原始标签透传给浏览器。
     *
     * @param throwable 触发兜底的原始异常
     * @return 500 状态码的 {@link HttpResult}，其数据为字符串，生成响应时按 text/html 输出
     */
    private Object errorHandleResult(Throwable throwable) {
        log.error("服务器异常", throwable);
        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            throwable.printStackTrace(printWriter);
        }
        String content = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="utf-8">
                    <title>TurboWeb Error</title>
                    <style>
                        body { font-family: Consolas, "Courier New", monospace; margin: 2em; }
                        h1 { color: #c0392b; }
                        .error-type { font-size: 1.1em; }
                        pre { background-color: #f6f8fa; border: 1px solid #e1e4e8; border-radius: 6px; padding: 1em; overflow-x: auto; }
                    </style>
                </head>
                <body>
                    <h1>500 Internal Server Error</h1>
                    <p class="error-type">异常类型：%s</p>
                    <pre>%s</pre>
                </body>
                </html>
                """.formatted(throwable.getClass().getName(), htmlEscape(writer.toString()));
        return HttpResult.err(content);
    }

    /**
     * 对文本做 HTML 实体转义，使异常栈等外来文本可安全嵌入页面正文。
     *
     * @param text 待转义文本
     * @return 转义后的文本
     */
    private static String htmlEscape(String text) {
        StringBuilder builder = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&#39;");
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }

    /**
     * 解析处理器实例集合，生成异常类型到调用器的不可变映射表。
     * <p>
     * 不依据注解认领，只按方法签名解析：公有且未由编译器合成、有且仅有一个参数
     * 且参数类型为异常类型（{@link Throwable} 自身或其子类）的方法视为异常处理方法；
     * 实例方法绑定所属实例，静态方法无需绑定；其余方法一律忽略。
     * 若出现两个相同的异常类型，抛出 {@link ExceptionHandlerException}。
     * </p>
     *
     * @param instances 异常处理器实例集合
     * @return 不可变的异常类型映射表
     * @throws ExceptionHandlerException     存在两个相同异常类型的处理方法
     * @throws TurboExceptionHandlerException 方法句柄不可访问
     */
    @SuppressWarnings("unchecked")
    private static Map<Class<? extends Throwable>, ExceptionHandlerInvoker> parseHandlers(Set<Object> instances) {
        Map<Class<? extends Throwable>, ExceptionHandlerInvoker> invokers = new HashMap<>();
        for (Object instance : instances) {
            for (Method method : instance.getClass().getMethods()) {
                if (!Modifier.isPublic(method.getModifiers())
                        || method.isSynthetic()
                        || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (!Throwable.class.isAssignableFrom(parameterType)) {
                    continue;
                }
                ExceptionHandlerInvoker invoker = new ExceptionHandlerInvoker(
                        bindHandle(method, instance),
                        (Class<? extends Throwable>) parameterType);
                if (invokers.putIfAbsent(invoker.exceptionClass(), invoker) != null) {
                    throw new ExceptionHandlerException("重复的异常处理器类型：" + parameterType.getName());
                }
            }
        }
        return Map.copyOf(invokers);
    }

    /**
     * 将处理方法解析为调用器可用的方法句柄。
     * <p>
     * 实例方法经 unreflect 后绑定所属实例；静态方法本身不含接收者，直接取句柄不绑定。
     * </p>
     *
     * @param method   异常处理方法
     * @param instance 方法所属实例
     * @return 可调用句柄
     * @throws TurboExceptionHandlerException 方法不可访问
     */
    private static MethodHandle bindHandle(Method method, Object instance) {
        try {
            MethodHandle methodHandle = MethodHandles.lookup().unreflect(method);
            if (!Modifier.isStatic(method.getModifiers())) {
                methodHandle = methodHandle.bindTo(instance);
            }
            return methodHandle;
        } catch (IllegalAccessException e) {
            throw new TurboExceptionHandlerException(e);
        }
    }
}
