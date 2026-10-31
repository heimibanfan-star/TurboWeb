package top.turboweb.http.context;

import org.junit.jupiter.api.Test;
import top.turboweb.http.request.FormRequestMessage;
import top.turboweb.http.request.JsonRequestMessage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求体内容可关闭性的形态契约：持有需归还资源的内容实现经 {@link AutoCloseContent}
 * 进入编排器的统一释放通道，无此类资源的内容实现不实现该接口、不背负关闭义务。
 */
class HttpContextContentContractTest {

    @Test
    void autoCloseContentExtendsAutoCloseable() {
        assertTrue(AutoCloseable.class.isAssignableFrom(AutoCloseContent.class),
                "AutoCloseContent 具备可关闭性");
    }

    @Test
    void formDataIsAutoCloseContentAndJsonDataIsNot() {
        assertTrue(AutoCloseContent.class.isAssignableFrom(FormRequestMessage.FormData.class),
                "持有解码器的表单内容经 AutoCloseContent 进入统一释放通道");
        assertFalse(AutoCloseable.class.isAssignableFrom(JsonRequestMessage.JsonData.class),
                "JSON 内容不持有需归还资源，不背负关闭义务");
    }
}
