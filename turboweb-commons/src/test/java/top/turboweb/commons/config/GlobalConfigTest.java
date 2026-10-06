package top.turboweb.commons.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局配置：默认值、可改、锁定后静默失效。
 *
 * <p>{@code isLock} 是 private static 且<b>没有解锁 API</b>（F8），只能用反射读回并在同一方法内
 * 还原；还原本身要断言，否则这条用例会把后续所有用例的默认语义改掉。反射依赖 commons 测试跑在
 * classpath（非模块路径）上——JPMS 化后会以 InaccessibleObjectException 红，那是预期的提醒。
 */
class GlobalConfigTest {

    private static boolean readIsLock() throws Exception {
        Field f = GlobalConfig.class.getDeclaredField("isLock");
        f.setAccessible(true);
        return f.getBoolean(null);
    }

    private static void writeIsLock(boolean value) throws Exception {
        Field f = GlobalConfig.class.getDeclaredField("isLock");
        f.setAccessible(true);
        f.set(null, value);
    }

    private static void writeCharset(String field, Charset value) throws Exception {
        Field f = GlobalConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, value);
    }

    @Test
    void defaultsAreUtf8() {
        assertEquals(StandardCharsets.UTF_8, GlobalConfig.getRequestCharset());
        assertEquals(StandardCharsets.UTF_8, GlobalConfig.getResponseCharset());
    }

    @Test
    void settersTakeEffectWhileUnlocked() throws Exception {
        Charset originalRequest = GlobalConfig.getRequestCharset();
        Charset originalResponse = GlobalConfig.getResponseCharset();
        boolean originalLock = readIsLock();
        assertFalse(originalLock, "本用例的前提是未锁定状态");
        try {
            GlobalConfig.setRequestCharset(StandardCharsets.ISO_8859_1);
            GlobalConfig.setResponseCharset(StandardCharsets.UTF_16);
            assertEquals(StandardCharsets.ISO_8859_1, GlobalConfig.getRequestCharset());
            assertEquals(StandardCharsets.UTF_16, GlobalConfig.getResponseCharset());
        } finally {
            writeCharset("requestCharset", originalRequest);
            writeCharset("responseCharset", originalResponse);
        }
        assertEquals(originalRequest, GlobalConfig.getRequestCharset(), "必须还原，否则污染后续用例");
        assertEquals(originalResponse, GlobalConfig.getResponseCharset());
    }

    @Test
    @DisplayName("钉住 F8：lockConfig() 一旦调用即不可逆，setter 之后静默失效")
    void lockConfigIsIrreversible() throws Exception {
        Charset originalRequest = GlobalConfig.getRequestCharset();
        Charset originalResponse = GlobalConfig.getResponseCharset();
        boolean originalLock = readIsLock();
        try {
            writeIsLock(false);
            // 先让两个值都偏离默认，才能看出"锁定后 setter 不再写入"
            GlobalConfig.setRequestCharset(StandardCharsets.ISO_8859_1);
            GlobalConfig.setResponseCharset(StandardCharsets.ISO_8859_1);
            Charset lockedRequest = GlobalConfig.getRequestCharset();
            Charset lockedResponse = GlobalConfig.getResponseCharset();

            GlobalConfig.lockConfig();
            assertTrue(readIsLock(), "lockConfig 只置位，没有任何公共 API 能复位");

            // 锁定后 setter 走 log.warn + return 分支：值必须保持在锁定前的那份
            GlobalConfig.setRequestCharset(StandardCharsets.UTF_16);
            GlobalConfig.setResponseCharset(StandardCharsets.UTF_16);
            assertEquals(lockedRequest, GlobalConfig.getRequestCharset(), "锁定后请求字符集不可变");
            assertEquals(lockedResponse, GlobalConfig.getResponseCharset(), "锁定后响应字符集不可变");
        } finally {
            writeIsLock(originalLock);
            writeCharset("requestCharset", originalRequest);
            writeCharset("responseCharset", originalResponse);
        }
        assertFalse(readIsLock(), "还原失败会锁死整个测试 JVM 的全局配置");
        // 还原后 setter 应当重新生效——顺带覆盖未锁定分支
        GlobalConfig.setRequestCharset(StandardCharsets.ISO_8859_1);
        assertEquals(StandardCharsets.ISO_8859_1, GlobalConfig.getRequestCharset());
        writeCharset("requestCharset", originalRequest);
        assertEquals(originalRequest, GlobalConfig.getRequestCharset());
    }
}
