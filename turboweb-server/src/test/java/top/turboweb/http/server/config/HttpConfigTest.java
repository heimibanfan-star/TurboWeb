package top.turboweb.http.server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpConfig} 的默认值与建造者校验矩阵（spec §5：server/config）。
 *
 * <p>与 {@link ServerConfigTest} 同风格：校验前置在链式方法里"立即抛出"，因此每个非法参数
 * 单独成例，并断言精确消息——消息是校验分支的可观测指纹，只断言异常类型时，把 `<` 改成
 * `<=`、或把消息文案写错，用例都不会红。
 */
class HttpConfigTest {

    @Test
    @DisplayName("默认值符合约定")
    void defaults() {
        int cpu = Runtime.getRuntime().availableProcessors();
        HttpConfig config = HttpConfig.builder().build();

        assertFalse(config.isSerializePerConnection(), "默认关闭每连接串行化");
        assertTrue(config.isShowRequestLog(), "默认显示请求日志");
        assertEquals(300000L, config.getSessionCheckTime());
        assertEquals(-1L, config.getSessionMaxNotUseTime());
        assertEquals(256L, config.getSessionCheckThreshold());
        assertFalse(config.isEnableHttpSchedulerLimit(), "默认关闭调度器限流");
        assertEquals(10000, config.getHttpSchedulerLimitCount());
        assertEquals(2000, config.getHttpSchedulerLimitCacheThread());
        assertEquals(500L, config.getHttpSchedulerLimitTimeout());
        assertEquals(4096, config.getDiskOpeThreadCacheQueue());
        assertEquals(6, config.getDiskOpeThreadCoreQueue());
        // 与 CPU 核数绑定的默认值必须现算，写死 2/4/8 会在别的机器上假红
        assertEquals(cpu * 2, config.getDiskOpeThreadMaxThreadNum());
    }

    @Test
    @DisplayName("HS-1 回归：sessionCheckTime 边界消息与判定一致（大于等于1000）")
    void sessionCheckTimeFloor() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HttpConfig.builder().sessionCheckTime(999L));
        assertEquals("sessionCheckTime必须大于等于1000", ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> HttpConfig.builder().sessionCheckTime(0L));
        // 判据 `< 1000`：边界 1000 合法（含），消息与 javadoc 同口径"大于等于1000"
        assertDoesNotThrow(() -> HttpConfig.builder().sessionCheckTime(1000L));
    }

    @Test
    @DisplayName("sessionCheckThreshold 必须大于0")
    void sessionCheckThresholdFloor() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HttpConfig.builder().sessionCheckThreshold(0L));
        assertEquals("sessionCheckThreshold必须大于0", ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> HttpConfig.builder().sessionCheckThreshold(-1L));
        assertDoesNotThrow(() -> HttpConfig.builder().sessionCheckThreshold(1L));
    }

    @Test
    @DisplayName("HS-2 回归：httpSchedulerLimitCount 校验消息为中文口径（大于等于0）")
    void httpSchedulerLimitCountNonNegative() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HttpConfig.builder().httpSchedulerLimitCount(-1));
        assertEquals("httpSchedulerLimitCount必须大于等于0", ex.getMessage());
        assertDoesNotThrow(() -> HttpConfig.builder().httpSchedulerLimitCount(0));
    }

    @Test
    @DisplayName("HS-2 回归：httpSchedulerLimitCacheThread 校验消息为中文口径（大于等于0）")
    void httpSchedulerLimitCacheThreadNonNegative() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HttpConfig.builder().httpSchedulerLimitCacheThread(-1));
        assertEquals("httpSchedulerLimitCacheThread必须大于等于0", ex.getMessage());
        assertDoesNotThrow(() -> HttpConfig.builder().httpSchedulerLimitCacheThread(0));
    }

    @Test
    @DisplayName("HS-2 回归：httpSchedulerLimitTimeout 校验消息为中文口径（大于等于0）")
    void httpSchedulerLimitTimeoutNonNegative() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HttpConfig.builder().httpSchedulerLimitTimeout(-1L));
        assertEquals("httpSchedulerLimitTimeout必须大于等于0", ex.getMessage());
        assertDoesNotThrow(() -> HttpConfig.builder().httpSchedulerLimitTimeout(0L));
    }

    @Test
    @DisplayName("diskOpeThreadCacheQueue 必须大于0")
    void diskOpeThreadCacheQueueFloor() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HttpConfig.builder().diskOpeThreadCacheQueue(0));
        assertEquals("diskOpeThreadCacheQueue必须大于0", ex.getMessage());
        assertDoesNotThrow(() -> HttpConfig.builder().diskOpeThreadCacheQueue(1));
    }

    @Test
    @DisplayName("diskOpeThreadCoreQueue 必须大于0")
    void diskOpeThreadCoreQueueFloor() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HttpConfig.builder().diskOpeThreadCoreQueue(0));
        assertEquals("diskOpeThreadCoreQueue必须大于0", ex.getMessage());
        assertDoesNotThrow(() -> HttpConfig.builder().diskOpeThreadCoreQueue(1));
    }

    @Test
    @DisplayName("diskOpeThreadMaxThreadNum 必须大于0")
    void diskOpeThreadMaxThreadNumFloor() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> HttpConfig.builder().diskOpeThreadMaxThreadNum(0));
        assertEquals("diskOpeThreadMaxThreadNum必须大于0", ex.getMessage());
        assertDoesNotThrow(() -> HttpConfig.builder().diskOpeThreadMaxThreadNum(1));
    }

    @Test
    @DisplayName("合法参数被原样承载（每个 setter 都不得吞掉入参）")
    void validValuesAreCarried() {
        HttpConfig config = HttpConfig.builder()
                .serializePerConnection(true)
                .showRequestLog(false)
                .sessionCheckTime(1234L)
                .sessionMaxNotUseTime(5678L)
                .sessionCheckThreshold(9L)
                .enableHttpSchedulerLimit(true)
                .httpSchedulerLimitCount(11)
                .httpSchedulerLimitCacheThread(22)
                .httpSchedulerLimitTimeout(33L)
                .diskOpeThreadCacheQueue(44)
                .diskOpeThreadCoreQueue(55)
                .diskOpeThreadMaxThreadNum(66)
                .build();

        assertTrue(config.isSerializePerConnection());
        assertFalse(config.isShowRequestLog(), "传入 false 必须覆盖默认 true");
        assertTrue(config.isEnableHttpSchedulerLimit());
        assertEquals(1234L, config.getSessionCheckTime());
        assertEquals(5678L, config.getSessionMaxNotUseTime());
        assertEquals(9L, config.getSessionCheckThreshold());
        assertEquals(11, config.getHttpSchedulerLimitCount());
        assertEquals(22, config.getHttpSchedulerLimitCacheThread());
        assertEquals(33L, config.getHttpSchedulerLimitTimeout());
        assertEquals(44, config.getDiskOpeThreadCacheQueue());
        assertEquals(55, config.getDiskOpeThreadCoreQueue());
        assertEquals(66, config.getDiskOpeThreadMaxThreadNum());
    }
}
