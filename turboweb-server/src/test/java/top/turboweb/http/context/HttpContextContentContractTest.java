package top.turboweb.http.context;

import top.turboweb.rest.core.HttpContent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpContent} 接口族的形态契约：基接口只声明内容类型读取，
 * 可关闭性由子接口 {@link AutoCloseContent} 组合承载。
 */
class HttpContextContentContractTest {

    @Test
    void contentIsNotAutoCloseable() {
        assertFalse(AutoCloseable.class.isAssignableFrom(HttpContent.class),
                "Content 不强制实现者可关闭，无释放需求的实现不应背负 close 义务");
    }

    @Test
    void autoCloseContentExtendsContentAndAutoCloseable() {
        assertTrue(HttpContent.class.isAssignableFrom(AutoCloseContent.class),
                "AutoCloseContent 是 Content 的子接口");
        assertTrue(AutoCloseable.class.isAssignableFrom(AutoCloseContent.class),
                "AutoCloseContent 具备可关闭性");
    }

    @Test
    void formContentIsAutoCloseContentAndJsonContentIsPlainContent() {
        assertTrue(AutoCloseContent.class.isAssignableFrom(HttpFormContent.class),
                "持有解码器的表单内容经 AutoCloseContent 进入统一释放通道");
        assertTrue(HttpContent.class.isAssignableFrom(HttpJsonContent.class),
                "JSON 内容实现 Content 读取契约");
        assertFalse(AutoCloseContent.class.isAssignableFrom(HttpJsonContent.class),
                "JSON 内容不持有需归还资源，实现纯 Content，不背负关闭义务");
    }
}
