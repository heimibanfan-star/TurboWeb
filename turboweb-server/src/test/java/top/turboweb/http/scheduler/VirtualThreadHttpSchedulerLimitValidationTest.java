package top.turboweb.http.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.testkit.Stubs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link VirtualThreadHttpScheduler} 限流参数的构造期校验
 * （spec §8 候选6 的回归面）。
 * <p>
 * 开启限流时并发许可数必须为正数：0 让信号量恒不可获取、负数无从发放许可，
 * 非正形态在构造期即被拒绝；未开启限流时并发数 0 属默认（无限流）形态，保持合法。
 * 两条用例都只走构造函数、不启动任何线程，故无资源需要回收（与日志开关用例同一手法）。
 */
class VirtualThreadHttpSchedulerLimitValidationTest {

    @Test
    @DisplayName("候选6 回归：开启限流时 limit 为 0 或负数在构造期抛出")
    void enabledLimitRejectsNonPositivePermits() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> new VirtualThreadHttpScheduler(null, Stubs.threadPool(), true, 0, 2000, 500));
        assertEquals("开启限流时最大并发线程数必须大于0", zero.getMessage());

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> new VirtualThreadHttpScheduler(null, Stubs.threadPool(), true, -1, 2000, 500));
        assertEquals("开启限流时最大并发线程数必须大于0", negative.getMessage());
    }

    @Test
    @DisplayName("候选6 回归：未开启限流时 limit=0 仍可构造")
    void disabledLimitKeepsZeroPermitsLegal() {
        VirtualThreadHttpScheduler scheduler = assertDoesNotThrow(
                () -> new VirtualThreadHttpScheduler(null, Stubs.threadPool(), false, 0, 0, 0),
                "限流未开启时零许可信号量不被触达，默认形态不得被校验误伤");
        assertNotNull(scheduler);
    }
}
