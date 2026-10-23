package top.turboweb.websocket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * {@link WebSocketException} 的消息透传与运行时异常身份。
 */
class WebSocketExceptionTest {

    @Test
    void carriesMessageAndIsRuntimeException() {
        WebSocketException exception = new WebSocketException("websocket session does not exist");

        assertEquals("websocket session does not exist", exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception);
    }
}
