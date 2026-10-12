package top.turboweb.http.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link ExceptionHandlerDefinition} 的字段持有、默认状态码与 setter 覆盖。
 */
class ExceptionHandlerDefinitionTest {

    private static MethodHandle handle() {
        return MethodHandles.constant(String.class, "handled");
    }

    @Test
    @DisplayName("构造器保存三个字段，状态码默认为 200")
    void constructorStoresFieldsAndDefaultsStatusToOk() {
        MethodHandle handle = handle();
        ExceptionHandlerDefinition definition =
                new ExceptionHandlerDefinition(ExceptionHandlerDefinitionTest.class, handle, IllegalStateException.class);

        assertSame(ExceptionHandlerDefinitionTest.class, definition.getHandlerClass());
        assertSame(handle, definition.getMethodHandler());
        assertEquals(IllegalStateException.class, definition.getExceptionClass());
        assertEquals(HttpResponseStatus.OK, definition.getHttpResponseStatus());
    }

    @Test
    @DisplayName("setHttpResponseStatus 覆盖默认值且即时可读")
    void setHttpResponseStatusOverridesDefault() {
        ExceptionHandlerDefinition definition = new ExceptionHandlerDefinition(
                ExceptionHandlerDefinitionTest.class, handle(), IllegalStateException.class);

        definition.setHttpResponseStatus(HttpResponseStatus.BAD_REQUEST);

        assertEquals(HttpResponseStatus.BAD_REQUEST, definition.getHttpResponseStatus());
        assertEquals(400, definition.getHttpResponseStatus().code());
    }
}
