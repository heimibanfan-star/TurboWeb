package top.turboweb.http.scheduler;

import org.junit.jupiter.api.Test;
import top.turboweb.http.testkit.Stubs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code showRequestLog} 开关的 L1 断言（spec §5：showRequestLog 开关）。
 * <p>
 * 真实的日志产出点在 {@code writeResponse} 之后的 ChannelFuture 监听里，需要真通道，归阶段 4。
 * 这里按 spec §7 第 8 条不碰日志内容，只钉“开关字段默认值 + setter 经接口透传”；
 * 字段为 {@code protected}，本类与被测类同包可直接读。
 */
class VirtualThreadHttpSchedulerLogSwitchTest {

    @Test
    void logSwitchDefaultsOnAndFollowsSetterThroughTheInterface() {
        VirtualThreadHttpScheduler scheduler = new VirtualThreadHttpScheduler(null, Stubs.threadPool());
        assertTrue(scheduler.showRequestLog, "默认开启请求日志");

        HttpScheduler asInterface = scheduler;
        asInterface.setShowRequestLog(false);
        assertFalse(scheduler.showRequestLog, "关闭开关必须直达字段");

        asInterface.setShowRequestLog(true);
        assertTrue(scheduler.showRequestLog);
    }
}
