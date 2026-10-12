package top.turboweb.serializer.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 序列化异常：三个构造器各自装配 message / cause 的语义。
 */
class SerializerExceptionTest {

    @Test
    void messageCtorKeepsMessageAndLeavesCauseNull() {
        SerializerException e = new SerializerException("boom");
        assertEquals("boom", e.getMessage());
        assertNull(e.getCause(), "仅消息构造器不携带原因");
        assertInstanceOf(RuntimeException.class, e, "非受检异常是序列化器门面统一抛出形态的前提");
    }

    @Test
    void messageAndCauseCtorKeepsBoth() {
        RuntimeException root = new RuntimeException("root");
        SerializerException e = new SerializerException("boom", root);
        assertEquals("boom", e.getMessage());
        assertSame(root, e.getCause(), "cause 必须原样保留，不得复制或改写");
    }

    @Test
    void causeCtorKeepsCauseAndDerivesMessage() {
        RuntimeException root = new RuntimeException("root");
        SerializerException e = new SerializerException(root);
        assertSame(root, e.getCause());
        // 单参 cause 构造走 Throwable(Throwable)：message 即 cause 的 toString
        assertEquals(root.toString(), e.getMessage());
    }
}
