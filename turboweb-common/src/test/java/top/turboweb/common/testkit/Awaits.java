package top.turboweb.common.testkit;

import java.util.function.BooleanSupplier;

/**
 * 测试夹具：把"等到条件成立"写成有截止时间的轮询。
 *
 * <p>spec §7 第 6 条禁止用 {@code Thread.sleep(固定时长)} 猜计数：那条写法要么慢要么抖。
 * 本夹具只在 deadline 内轮询谓词，条件一成立立刻返回，因此既不为时序买单、
 * 也不把"没等到"误报成"等错了"。返回 false 表示整个窗口内条件始终不成立——
 * 这正是负向断言需要的证据。
 */
public final class Awaits {

    /** 轮询间隔：足够小以捕捉 20ms 级节拍，足够大以免空转。 */
    private static final long POLL_INTERVAL_MILLIS = 5L;

    private Awaits() {
    }

    public static boolean awaitUntil(BooleanSupplier condition, long deadlineMillis) {
        long deadline = System.nanoTime() + deadlineMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        // 出界前最后一次确认，避免"恰好在 deadline 上成立"被判成没成立
        return condition.getAsBoolean();
    }
}
