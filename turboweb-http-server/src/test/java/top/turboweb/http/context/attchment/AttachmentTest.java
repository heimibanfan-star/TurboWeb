package top.turboweb.http.context.attchment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DefaultAttachment} 的按类型/按键挂载与读取（spec §5：context 行）。
 */
class AttachmentTest {

    @Test
    void attachByObjectUsesTheFqcnAsKey() {
        DefaultAttachment attachment = new DefaultAttachment();
        attachment.attach("hello");
        assertEquals("hello", attachment.getAttachment("java.lang.String"));
        assertSame("hello", attachment.getAttachment(String.class));
    }

    @Test
    void attachWithExplicitKeyIsReadBackByKey() {
        DefaultAttachment attachment = new DefaultAttachment();
        attachment.attach("k", 42);
        assertEquals(42, attachment.getAttachment("k"));
        assertEquals(42, attachment.getAttachment("k", Integer.class));
    }

    @Test
    void missingKeyReturnsNullForBothOverloads() {
        DefaultAttachment attachment = new DefaultAttachment();
        assertNull(attachment.getAttachment("nope"));
        assertNull(attachment.getAttachment("nope", String.class));
    }

    @Test
    void wrongTypeCastThrows() {
        DefaultAttachment attachment = new DefaultAttachment();
        attachment.attach("k", 42);
        assertThrows(ClassCastException.class, () -> attachment.getAttachment("k", String.class));
    }

    @Test
    void sameKeyOverwritesThePreviousValue() {
        DefaultAttachment attachment = new DefaultAttachment();
        attachment.attach("k", 1);
        attachment.attach("k", 2);
        assertEquals(2, attachment.getAttachment("k"));
    }

    @Test
    void explicitKeyDoesNotCollideWithTheFqcnKey() {
        DefaultAttachment attachment = new DefaultAttachment();
        attachment.attach(7);              // 键 = java.lang.Integer
        attachment.attach("java.lang.Integer", 9);
        assertEquals(9, attachment.getAttachment("java.lang.Integer"));
        // 类型键查的是同一字符串键，因此读到被覆盖后的值
        assertEquals(9, attachment.getAttachment(Integer.class));
    }
}
