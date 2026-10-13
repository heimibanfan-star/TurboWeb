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
 * 限流参数交由虚拟线程资源管理：开启限流时构造受限资源，并发执行数必须为正数
 * （0 或负数无从发放资源），最大挂起数与最大挂起时间不能为负数，
 * 不合规形态在构造期即被拒绝；未开启限流时构造非受限资源，三个限流参数不参与校验，
 * 默认（无限流）形态保持合法。
 * 用例都只走构造函数、不启动任何线程，故无资源需要回收（与日志开关用例同一手法）。
 */
class VirtualThreadHttpSchedulerLimitValidationTest {

    @Test
    @DisplayName("候选6 回归：开启限流时 limit 为 0 或负数在构造期抛出")
    void enabledLimitRejectsNonPositivePermits() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> new VirtualThreadHttpScheduler(null, Stubs.threadPool(), true, 0, 2000, 500));
        assertEquals("资源数必须大于0", zero.getMessage());

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> new VirtualThreadHttpScheduler(null, Stubs.threadPool(), true, -1, 2000, 500));
        assertEquals("资源数必须大于0", negative.getMessage());
    }

    @Test
    @DisplayName("开启限流时最大挂起数或最大挂起时间为负在构造期抛出")
    void enabledLimitRejectsNegativeSuspendArguments() {
        IllegalArgumentException negativeSuspend = assertThrows(IllegalArgumentException.class,
                () -> new VirtualThreadHttpScheduler(null, Stubs.threadPool(), true, 8, -1, 500));
        assertEquals("最大挂起数不能小于0", negativeSuspend.getMessage());

        IllegalArgumentException negativeTimeout = assertThrows(IllegalArgumentException.class,
                () -> new VirtualThreadHttpScheduler(null, Stubs.threadPool(), true, 8, 2000, -1));
        assertEquals("最大挂起时间不能小于0", negativeTimeout.getMessage());
    }

    @Test
    @DisplayName("候选6 回归：未开启限流时 limit=0 仍可构造")
    void disabledLimitKeepsZeroPermitsLegal() {
        VirtualThreadHttpScheduler scheduler = assertDoesNotThrow(
                () -> new VirtualThreadHttpScheduler(null, Stubs.threadPool(), false, 0, 0, 0),
                "限流未开启时构造非受限资源，限流参数不参与校验，默认形态不得被误伤");
        assertNotNull(scheduler);
    }
}
