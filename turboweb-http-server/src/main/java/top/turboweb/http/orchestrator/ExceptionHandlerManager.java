package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.http.response.HttpResult;
import top.turboweb.http.exception.ExceptionHandlerException;
import top.turboweb.http.exception.RouteHandlerException;

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
 * 两个方法声明相同异常类型时抛出 {@link IllegalStateException} 拒绝构建。
 * </p>
 * <p>
 * 异常发生时经 {@link #handle(Throwable)} 处理：自异常自身的类型起沿继承链逐级向上，
 * 优先命中绑定最近异常类型的处理器并透传其返回值；
 * 整条继承链均无匹配时，路由异常按错误码携带的 HTTP 状态码兜底——
 * 客户端状态码（4xx）返回对应状态码、展示异常消息的 {@link HttpResult}，服务器状态码（5xx）走 500 兜底；
 * 其余异常与处理器自身执行失败时，
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
     * 抛出 {@link IllegalStateException}。
     * </p>
     *
     * @param handlers 异常处理器实例集合，不能为 null
     * @throws NullPointerException          handlers 为 null
     * @throws IllegalStateException     存在两个相同异常类型的处理方法
     * @throws ExceptionHandlerException 方法句柄不可访问
     */
    public ExceptionHandlerManager(Set<Object> handlers) {
        this.handlers = parseHandlers(Objects.requireNonNull(handlers, "handlers must not be null"));
    }

    /**
     * 匹配异常处理器处理异常。
     * <p>
     * 自异常自身的类型起沿继承链逐级向上查找，优先命中绑定最近父类异常的处理器，
     * 命中后执行并返回处理方法的返回值。
     * 若整条继承链都没有注册处理器则兜底：路由异常按错误码携带的 HTTP 状态码分流，
     * 客户端状态码（4xx）返回对应状态码、展示异常消息的 {@link HttpResult} 且不记录错误日志，
     * 服务器状态码（5xx）走 500 兜底；
     * 其余无匹配或处理器执行中自身抛出异常的情况，
     * 构建 500 状态码、text/html 形式并携带完整异常栈的 {@link HttpResult} 兜底响应返回，同时记录日志。
     * </p>
     *
     * @param throwable 待处理的异常
     * @return 命中的处理方法的返回值；兜底时为客户端错误码或 500 状态码的 {@link HttpResult}
     */
    public Object handle(Throwable throwable) {
        ExceptionHandlerInvoker invoker = findInvoker(throwable);
        if (invoker == null) {
            return fallbackHandleResult(throwable);
        }
        try {
            return invoker.invoke(throwable);
        } catch (Throwable handlerFailure) {
            log.error("Failed to execute exception handler", handlerFailure);
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
     * 构建未命中任何异常处理器时的兜底响应：路由异常按错误码携带的 HTTP 状态码分流，
     * 状态码低于 500（客户端错误）时生成 HTML 错误页面展示异常消息并按该状态码返回——
     * 消息携带请求方法、被请求 URI 或缺值参数名（均来自客户端输入），经 HTML 转义后嵌入页面，
     * 其中的标记字符不会作为原始标签透传给浏览器；本情况不是服务器故障，不记录错误日志、不输出异常栈。
     * 服务器状态码（500 及以上）的路由异常与其余全部异常走 500 异常栈兜底页并记录错误日志。
     *
     * @param throwable 触发兜底的原始异常
     * @return 对应状态码的 {@link HttpResult}，其数据为字符串，生成响应时按 text/html 输出
     */
    private Object fallbackHandleResult(Throwable throwable) {
        if (throwable instanceof RouteHandlerException routeHandlerException) {
            HttpResponseStatus status = routeHandlerException.getCode().getHttpStatus();
            if (status.code() < 500) {
                return HttpResult.create(status.code(), ExceptionMessageHelper.clientErrorMessage(throwable, status));
            }
        }
        return errorHandleResult(throwable);
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
        log.error("Server error", throwable);
        return HttpResult.err(ExceptionMessageHelper.errorMessage(throwable));
    }

    /**
     * 解析处理器实例集合，生成异常类型到调用器的不可变映射表。
     * <p>
     * 不依据注解认领，只按方法签名解析：公有且未由编译器合成、有且仅有一个参数
     * 且参数类型为异常类型（{@link Throwable} 自身或其子类）的方法视为异常处理方法；
     * 实例方法绑定所属实例，静态方法无需绑定；其余方法一律忽略。
     * 若出现两个相同的异常类型，抛出 {@link IllegalStateException}。
     * </p>
     *
     * @param instances 异常处理器实例集合
     * @return 不可变的异常类型映射表
     * @throws IllegalStateException     存在两个相同异常类型的处理方法
     * @throws ExceptionHandlerException 方法句柄不可访问
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
                    throw new IllegalStateException("Duplicate exception handler type: " + parameterType.getName());
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
     * @throws ExceptionHandlerException 方法不可访问
     */
    private static MethodHandle bindHandle(Method method, Object instance) {
        try {
            MethodHandle methodHandle = MethodHandles.lookup().unreflect(method);
            if (!Modifier.isStatic(method.getModifiers())) {
                methodHandle = methodHandle.bindTo(instance);
            }
            return methodHandle;
        } catch (IllegalAccessException e) {
            throw new ExceptionHandlerException(e);
        }
    }
}
