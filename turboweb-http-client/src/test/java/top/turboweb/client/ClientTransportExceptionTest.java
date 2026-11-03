package top.turboweb.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 传输异常的语义：说明与原因按构造如实携带。
 */
class ClientTransportExceptionTest {

    @Test
    @DisplayName("带原因构造如实携带说明与原因")
    void messageAndCauseCarried() {
        IllegalStateException cause = new IllegalStateException("root");

        ClientTransportException e = new ClientTransportException("传输失败", cause);

        assertEquals("传输失败", e.getMessage());
        assertEquals(cause, e.getCause());
    }

    @Test
    @DisplayName("仅说明构造时原因为空")
    void messageOnlyCarried() {
        ClientTransportException e = new ClientTransportException("提前终止");

        assertEquals("提前终止", e.getMessage());
        assertNull(e.getCause());
    }
}
