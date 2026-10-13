package top.turboweb.http.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.exception.TurboExceptionHandlerException;
import top.turboweb.http.exception.ExceptionHandlerException;
import top.turboweb.http.orchestrator.ExceptionHandlerInvoker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ExceptionHandlerInvoker} 的构造校验与调用判定。
 *
 * <p>句柄一律经 findVirtual + bindTo 造真绑定，与 initContainer 产物同形；
 * 执行与否的观测靠夹具方法上的调用计数。
 */
class ExceptionHandlerInvokerTest {

    /** 记录执行次数的处理器夹具。 */
    public static class Recorder {
        public int calls;

        public String onRuntimeException(RuntimeException e) {
            calls++;
            return "rte:" + e.getMessage();
        }

        public String onIllegalState(IllegalStateException e) {
            calls++;
            return "ise:" + e.getMessage();
        }

        public void onVoid(Exception e) {
            calls++;
        }

        public String rethrow(RuntimeException e) {
            throw new IllegalArgumentException("from-handler");
        }

        public String throwChecked(RuntimeException e) throws CheckedFailure {
            throw new CheckedFailure();
        }
    }

    /** 处理器声明的受检异常：证明受检抛出同样被包装。 */
    public static class CheckedFailure extends Exception {
    }

    private static MethodHandle bound(Recorder recorder, String methodName, Class<?> returnType, Class<?> paramType) {
        try {
            return MethodHandles.lookup()
                    .findVirtual(Recorder.class, methodName, MethodType.methodType(returnType, paramType))
                    .bindTo(recorder);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("句柄或异常类型为 null 时构造抛 NullPointerException")
    void constructorRejectsNullHandleAndNullExceptionClass() {
        Recorder recorder = new Recorder();
        MethodHandle handle = bound(recorder, "onRuntimeException", String.class, RuntimeException.class);

        assertThrows(NullPointerException.class,
                () -> new ExceptionHandlerInvoker(null, RuntimeException.class));
        assertThrows(NullPointerException.class,
                () -> new ExceptionHandlerInvoker(handle, null));
    }

    @Test
    @DisplayName("exceptionClass 回读构造时绑定的异常类型")
    void accessorReturnsBoundExceptionClass() {
        Recorder recorder = new Recorder();
        MethodHandle handle = bound(recorder, "onRuntimeException", String.class, RuntimeException.class);

        ExceptionHandlerInvoker invoker =
                new ExceptionHandlerInvoker(handle, RuntimeException.class);

        assertEquals(RuntimeException.class, invoker.exceptionClass());
    }

    @Test
    @DisplayName("异常可赋值给绑定类型时执行处理器并回传返回值，子类异常同样命中")
    void assignableExceptionIsHandledIncludingSubclass() {
        Recorder recorder = new Recorder();
        MethodHandle handle = bound(recorder, "onRuntimeException", String.class, RuntimeException.class);
        ExceptionHandlerInvoker invoker =
                new ExceptionHandlerInvoker(handle, RuntimeException.class);

        Object result = invoker.invoke(new IllegalStateException("boom"));

        assertEquals("rte:boom", result);
        assertEquals(1, recorder.calls, "命中即执行处理方法");
    }

    @Test
    @DisplayName("异常不可赋值时抛 ExceptionHandlerInvokerException，不执行处理器")
    void nonAssignableExceptionThrowsWithoutExecuting() {
        Recorder recorder = new Recorder();
        MethodHandle handle = bound(recorder, "onIllegalState", String.class, IllegalStateException.class);
        ExceptionHandlerInvoker invoker =
                new ExceptionHandlerInvoker(handle, IllegalStateException.class);

        ExceptionHandlerException ex = assertThrows(ExceptionHandlerException.class,
                () -> invoker.invoke(new IllegalArgumentException("no-match")));

        assertTrue(ex.getMessage().contains(IllegalStateException.class.getName()), ex.getMessage());
        assertTrue(ex.getMessage().contains(IllegalArgumentException.class.getName()), ex.getMessage());
        assertEquals(0, recorder.calls, "未命中不得执行处理方法");
    }

    @Test
    @DisplayName("异常为 null 视同不可赋值，抛异常且不执行处理器")
    void nullThrowableThrowsWithoutExecuting() {
        Recorder recorder = new Recorder();
        MethodHandle handle = bound(recorder, "onRuntimeException", String.class, RuntimeException.class);
        ExceptionHandlerInvoker invoker =
                new ExceptionHandlerInvoker(handle, RuntimeException.class);

        assertThrows(ExceptionHandlerException.class, () -> invoker.invoke(null));
        assertEquals(0, recorder.calls);
    }

    @Test
    @DisplayName("void 处理器命中后照常执行，返回值为 null")
    void voidHandlerRunsAndReturnsNull() {
        Recorder recorder = new Recorder();
        MethodHandle handle = bound(recorder, "onVoid", void.class, Exception.class);
        ExceptionHandlerInvoker invoker =
                new ExceptionHandlerInvoker(handle, Exception.class);

        assertNull(invoker.invoke(new IllegalStateException("x")));
        assertEquals(1, recorder.calls, "返回值 null 不排除已执行，计数证明执行发生");
    }

    @Test
    @DisplayName("处理器抛运行时异常包装为 TurboExceptionHandlerException，原异常留作 cause")
    void handlerRuntimeExceptionIsWrapped() {
        Recorder recorder = new Recorder();
        MethodHandle handle = bound(recorder, "rethrow", String.class, RuntimeException.class);
        ExceptionHandlerInvoker invoker =
                new ExceptionHandlerInvoker(handle, RuntimeException.class);

        TurboExceptionHandlerException ex = assertThrows(TurboExceptionHandlerException.class,
                () -> invoker.invoke(new RuntimeException("in")));

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertEquals("from-handler", ex.getCause().getMessage());
    }

    @Test
    @DisplayName("处理器抛受检异常同样包装为 TurboExceptionHandlerException")
    void handlerCheckedExceptionIsWrapped() {
        Recorder recorder = new Recorder();
        MethodHandle handle = bound(recorder, "throwChecked", String.class, RuntimeException.class);
        ExceptionHandlerInvoker invoker =
                new ExceptionHandlerInvoker(handle, RuntimeException.class);

        TurboExceptionHandlerException ex = assertThrows(TurboExceptionHandlerException.class,
                () -> invoker.invoke(new RuntimeException("in")));

        assertInstanceOf(CheckedFailure.class, ex.getCause(), "受检抛出不得逃逸出包装");
    }
}
