package top.turboweb.http.context;

import org.junit.jupiter.api.Test;
import top.turboweb.http.request.FormRequestMessage;
import top.turboweb.http.request.JsonRequestMessage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求体内容可关闭性的形态契约：持有需归还资源的内容实现 {@link AutoCloseable}
 * 进入编排器的统一释放通道，无此类资源的内容实现不实现该接口、不背负关闭义务。
 */
class HttpContextContentContractTest {

    @Test
    void multipartFormDataIsAutoCloseableAndPlainFormIsNot() {
        assertTrue(AutoCloseable.class.isAssignableFrom(FormRequestMessage.MultipartFormData.class),
                "持有解码器的多部分表单内容实现 AutoCloseable 进入统一释放通道");
        assertFalse(AutoCloseable.class.isAssignableFrom(FormRequestMessage.FormData.class),
                "普通表单内容为堆上字符串，不背负关闭义务");
    }

    @Test
    void jsonStringContentIsNotAutoCloseable() {
        assertFalse(AutoCloseable.class.isAssignableFrom(String.class),
                "JSON 内容为字符串，不持有需归还资源，不背负关闭义务");
    }
}
