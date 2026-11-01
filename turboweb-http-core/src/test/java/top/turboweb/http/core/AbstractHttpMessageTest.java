package top.turboweb.http.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbstractHttpMessage} 的契约：消息行与消息体内容的构造期持有、
 * 头部经 {@link #headers()} 返回的自有 {@link Headers} 实体读写。
 * 头实体的读写语义（大小写不敏感、多值顺序、只读视图等）由 {@link HeadersTest} 承接。
 */
class AbstractHttpMessageTest {

    /** 头部操作全量由抽象类实现，测试用空实现子类承载 */
    private static final class TestMessage extends AbstractHttpMessage<String, Object> {

        private TestMessage(String line, Object content) {
            super(line, content);
        }
    }

    /** 头部用例不经行与内容通道，统一带入的标记内容 */
    private static final Object NO_CONTENT = new Object();

    private static TestMessage message() {
        return new TestMessage("LINE", NO_CONTENT);
    }

    // ---------- 行与内容的持有 ----------

    @Test
    void carriesConstructorInjectedLineAndContent() {
        Object content = new Object();
        TestMessage message = new TestMessage("L", content);

        assertSame("L", message.line(), "消息行按入参原样持有");
        assertSame(content, message.content(), "内容按入参原样持有");
    }

    @Test
    void nullLineOrContentRejectedOnConstruction() {
        assertEquals("line must not be null",
                assertThrows(NullPointerException.class, () -> new TestMessage(null, NO_CONTENT)).getMessage());
        assertEquals("content must not be null",
                assertThrows(NullPointerException.class, () -> new TestMessage("L", null)).getMessage());
    }

    // ---------- 消息头实体的获取 ----------

    @Test
    void headersReturnsSameLiveEntityOnRepeatedAccess() {
        TestMessage message = message();

        Headers headers = message.headers();

        assertNotNull(headers, "消息自有头实体构造即可用");
        assertSame(headers, message.headers(), "重复访问返回同一实体");
        assertTrue(headers.isEmpty(), "构造期无任何消息头");
    }

    @Test
    void headerEntityWritesActDirectlyOnMessage() {
        TestMessage message = message();

        message.headers().set("Content-Type", "application/json");
        message.headers().add("X-Multi", "a");
        message.headers().add("X-Multi", "b");

        assertEquals("application/json", message.headers().get("content-type"), "写入后经消息头实体按大小写不敏感读回");
        assertEquals("a", message.headers().get("X-Multi"), "同名多值取首值");
        message.headers().remove("X-Multi");
        assertNull(message.headers().get("X-Multi"), "删除直接作用于消息自有实体");
    }
}
