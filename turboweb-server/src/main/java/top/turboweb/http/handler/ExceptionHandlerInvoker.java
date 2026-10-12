package top.turboweb.http.handler;

import top.turboweb.commons.exception.TurboExceptionHandlerException;
import top.turboweb.http.exception.ExceptionHandlerException;

import java.lang.invoke.MethodHandle;
import java.util.Objects;

/**
 * 异常处理器调用器。
 * <p>
 * 封装单个异常处理器的执行要素：已绑定处理器实例的方法句柄与该处理器负责的异常类型。
 * 调用 {@link #invoke(Throwable)} 时先判定异常可否赋值给绑定的异常类型，
 * 可赋值才执行处理方法，不可赋值则不执行并抛出 {@link ExceptionHandlerException}。
 * </p>
 */
public class ExceptionHandlerInvoker {

    private final MethodHandle methodHandle;

    private final Class<? extends Throwable> exceptionClass;

    /**
     * 创建异常处理器调用器。
     *
     * @param methodHandle   异常处理方法句柄，应在构造前已绑定处理器实例，不能为 null
     * @param exceptionClass 该处理器负责的异常类型，不能为 null
     * @throws NullPointerException methodHandle 或 exceptionClass 为 null
     */
    public ExceptionHandlerInvoker(MethodHandle methodHandle, Class<? extends Throwable> exceptionClass) {
        this.methodHandle = Objects.requireNonNull(methodHandle, "methodHandle must not be null");
        this.exceptionClass = Objects.requireNonNull(exceptionClass, "exceptionClass must not be null");
    }

    /**
     * 调用异常处理器处理异常。
     * <p>
     * 异常可赋值给绑定的异常类型（本类或其子类实例）时执行处理方法并返回其返回值；
     * 不可赋值（含异常为 null）时不执行处理方法，抛出 {@link ExceptionHandlerException}。
     * 处理方法为 void 时执行后返回 null。
     * 处理方法执行中抛出的任何异常一律包装为 {@link TurboExceptionHandlerException} 抛出，
     * 原始异常保留为 cause。
     * </p>
     *
     * @param throwable 待处理的异常
     * @return 处理方法的返回值；方法为 void 时为 null
     * @throws ExceptionHandlerException 异常不可赋值给绑定的异常类型，处理方法未被执行
     * @throws TurboExceptionHandlerException   处理方法自身抛出异常
     */
    public Object invoke(Throwable throwable) {
        if (!this.exceptionClass.isInstance(throwable)) {
            throw new ExceptionHandlerException(String.format(
                    "异常处理器绑定 %s，不支持处理: %s",
                    this.exceptionClass.getName(),
                    throwable == null ? "null" : throwable.getClass().getName()));
        }
        try {
            return this.methodHandle.invoke(throwable);
        } catch (Throwable e) {
            throw new TurboExceptionHandlerException("异常处理器执行失败", e);
        }
    }

    /**
     * 获取该处理器负责的异常类型。
     *
     * @return 绑定的异常类型
     */
    public Class<? extends Throwable> exceptionClass() {
        return this.exceptionClass;
    }
}
