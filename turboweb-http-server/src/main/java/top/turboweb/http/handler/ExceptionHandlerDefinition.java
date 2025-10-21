package top.turboweb.http.handler;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.lang.invoke.MethodHandle;

/**
 * 异常处理器定义信息。
 * <p>
 * 该类用于描述一个异常处理器的完整定义，包括：
 * <ul>
 *     <li>所属的处理器类</li>
 *     <li>具体执行方法的 {@link MethodHandle}</li>
 *     <li>关联的异常类型</li>
 *     <li>处理后返回的 HTTP 状态码</li>
 * </ul>
 * 框架在发生异常时会根据异常类型匹配到对应的 {@link ExceptionHandlerDefinition}，
 * 并通过 {@link MethodHandle} 执行对应的异常处理方法。
 * </p>
 */
public class ExceptionHandlerDefinition {

    /**
     * 定义该异常处理方法所属的类。
     */
    private final Class<?> handlerClass;

    /**
     * 异常处理方法的句柄。
     * <p>
     * 通过 {@link MethodHandle} 可在运行时直接调用该方法，
     * 避免传统反射调用的性能开销。
     * </p>
     */
    private final MethodHandle methodHandle;

    /**
     * 此异常处理器对应的异常类型。
     * <p>
     * 当捕获到该类型或其子类异常时，会调用该处理器。
     * </p>
     */
    private final Class<? extends Throwable> exceptionClass;

    /**
     * 异常处理后返回的 HTTP 状态码。
     * <p>默认值为 {@link HttpResponseStatus#OK}。</p>
     */
    private HttpResponseStatus httpResponseStatus = HttpResponseStatus.OK;

    /**
     * 创建异常处理器定义。
     *
     * @param handlerClass   异常处理方法所属的类
     * @param methodHandle   异常处理方法的句柄
     * @param exceptionClass 该处理器对应的异常类型
     */
    public ExceptionHandlerDefinition(Class<?> handlerClass, MethodHandle methodHandle, Class<? extends Throwable> exceptionClass) {
        this.handlerClass = handlerClass;
        this.methodHandle = methodHandle;
        this.exceptionClass = exceptionClass;
    }

    /**
     * 设置异常处理后返回的 HTTP 状态码。
     *
     * @param httpResponseStatus HTTP 状态码
     */
    public void setHttpResponseStatus(HttpResponseStatus httpResponseStatus) {
        this.httpResponseStatus = httpResponseStatus;
    }

    /**
     * 获取异常处理方法的句柄。
     *
     * @return 异常处理方法的 {@link MethodHandle}
     */
    public MethodHandle getMethodHandler() {
        return methodHandle;
    }

    /**
     * 获取该处理器对应的异常类型。
     *
     * @return 异常类
     */
    public Class<? extends Throwable> getExceptionClass() {
        return exceptionClass;
    }

    /**
     * 获取异常处理后返回的 HTTP 状态码。
     *
     * @return HTTP 响应状态
     */
    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

    /**
     * 获取定义该异常处理方法的类。
     *
     * @return 处理器类
     */
    public Class<?> getHandlerClass() {
        return handlerClass;
    }
}
