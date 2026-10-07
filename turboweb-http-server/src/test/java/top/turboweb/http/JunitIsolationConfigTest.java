package top.turboweb.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 隔离配置自检：`junit-platform.properties` 里的超时键必须能被引擎按字面接受。
 * <p>
 * 引擎对无法解析的 thread mode 只打一条 JUL 警告再静默回退 SAME_THREAD，Gradle 默认测试输出里
 * 看不见——写错的值不会让用例变红，只会让"30s 兜底"退化成注释里的承诺（spec §7.2）。
 * 这里把三处取值钉成断言，写错就红。
 */
class JunitIsolationConfigTest {

    private static final String PARALLEL_KEY = "junit.jupiter.execution.parallel.enabled";
    private static final String TIMEOUT_KEY = "junit.jupiter.execution.timeout.default";
    private static final String THREAD_MODE_KEY = "junit.jupiter.execution.timeout.thread.mode.default";

    private Properties loadFromFile() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/junit-platform.properties")) {
            assertNotNull(in, "测试类路径上必须能读到 junit-platform.properties");
            props.load(in);
        }
        return props;
    }

    @Test
    @DisplayName("并行执行显式关闭（四处JVM全局静态）")
    void parallelExecutionIsDisabled() throws Exception {
        assertEquals("false", loadFromFile().getProperty(PARALLEL_KEY),
                PARALLEL_KEY + " 必须显式为 false，否则用例间共享 GlobalConfig 等静态状态");
    }

    @Test
    @DisplayName("默认超时是引擎可解析的时长写法")
    void defaultTimeoutIsParsable() throws Exception {
        String value = loadFromFile().getProperty(TIMEOUT_KEY);
        assertNotNull(value, "缺少 " + TIMEOUT_KEY);
        assertTrue(value.trim().matches("\\d+\\s*(ns|us|ms|s|m|h|d)"),
                TIMEOUT_KEY + " 必须是 <数字><单位>，实际: " + value);
    }

    @Test
    @DisplayName("超时线程模式取值合法且为SEPARATE_THREAD")
    void threadModeIsLegalAndSeparate() throws Exception {
        String value = loadFromFile().getProperty(THREAD_MODE_KEY);
        assertNotNull(value, "缺少 " + THREAD_MODE_KEY);
        assertSame(Timeout.ThreadMode.SEPARATE_THREAD, Timeout.ThreadMode.valueOf(value.trim()),
                THREAD_MODE_KEY + " 写成非法值会被静默回退成 SAME_THREAD——"
                        + " SAME_THREAD 无法打断阻塞调用（如未设超时的 socket 读），兜底形同不存在。实际值: " + value);
    }
}
