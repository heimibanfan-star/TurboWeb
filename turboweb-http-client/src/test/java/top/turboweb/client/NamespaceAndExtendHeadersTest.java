package top.turboweb.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.client.annotation.ExtendHeaders;
import top.turboweb.client.annotation.Header;
import top.turboweb.client.annotation.Namespace;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 命名空间与扩展头注解的读出用例：注解须运行期保留，
 * 值条目的多值缺省为空数组。
 */
class NamespaceAndExtendHeadersTest {

    /**
     * 标注夹具：类型级带命名空间与两条扩展头，其中一条多值缺省，方法级带命名空间。
     */
    @Namespace("order")
    @ExtendHeaders({
            @Header(name = "X-Trace-Id", values = {"a1", "a2"}),
            @Header(name = "X-Env")
    })
    private interface OrderPort {

        @Namespace("detail")
        void detail();
    }

    @Test
    @DisplayName("类型级注解按标注值读出")
    void typeLevelAnnotationsExposeDeclaredValues() {
        Namespace namespace = OrderPort.class.getAnnotation(Namespace.class);
        assertNotNull(namespace);
        assertEquals("order", namespace.value());

        ExtendHeaders extendHeaders = OrderPort.class.getAnnotation(ExtendHeaders.class);
        assertNotNull(extendHeaders);
        assertEquals(2, extendHeaders.value().length);
        assertEquals("X-Trace-Id", extendHeaders.value()[0].name());
        assertArrayEquals(new String[]{"a1", "a2"}, extendHeaders.value()[0].values());
        assertEquals("X-Env", extendHeaders.value()[1].name());
        assertArrayEquals(new String[0], extendHeaders.value()[1].values());
    }

    @Test
    @DisplayName("方法级命名空间按标注值读出")
    void methodLevelNamespaceExposesDeclaredValue() throws Exception {
        Method detail = OrderPort.class.getDeclaredMethod("detail");
        Namespace namespace = detail.getAnnotation(Namespace.class);

        assertNotNull(namespace);
        assertEquals("detail", namespace.value());
    }
}
