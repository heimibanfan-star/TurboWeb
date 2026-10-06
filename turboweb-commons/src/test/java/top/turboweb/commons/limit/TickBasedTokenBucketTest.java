package top.turboweb.commons.limit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.testkit.Awaits;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 节拍式令牌桶：补币、上限、关闭（spec §5"两个 token bucket 补币/上限"）。
 *
 * <p>没有假时钟可用，靠注入极端 interval 把不确定变确定：
 * 1 小时 = "这一辈子不会有令牌"，20ms = "两秒内必然有"，500ms = 上限可即时判定。
 */
class TickBasedTokenBucketTest {

    @Test
    void startsEmptyAndStaysEmptyWithHugeInterval() {
        TickBasedTokenBucket bucket = new TickBasedTokenBucket(1, Duration.ofHours(1));
        assertFalse(bucket.tryAcquire(), "生成线程先 park 再补币，构造后立即取必然为空");
    }

    @Test
    void refillsOnTickWithFastInterval() {
        TickBasedTokenBucket bucket = new TickBasedTokenBucket(1, Duration.ofMillis(20));
        assertTrue(Awaits.awaitUntil(bucket::tryAcquire, 2_000), "20ms 节拍应在 2s 窗口内补出令牌");
    }

    @Test
    void neverExceedsMaxTokenCount() {
        TickBasedTokenBucket bucket = new TickBasedTokenBucket(1, Duration.ofMillis(500));
        assertTrue(Awaits.awaitUntil(bucket::tryAcquire, 5_000), "500ms 内应补出 1 枚令牌");
        assertFalse(bucket.tryAcquire(), "上限 1：同一拍不会补出第二枚，下一拍还要再等 500ms");
    }

    @Test
    @DisplayName("钉住语义：close 只在下一次循环顶部生效，最多再漏出一枚令牌")
    void closeStopsRefillWithinOneTick() throws IOException {
        TickBasedTokenBucket bucket = new TickBasedTokenBucket(1, Duration.ofMillis(20));
        bucket.close();

        // close 是 best-effort：调用时生成线程可能已经 park 在这一拍上，醒来照样补一枚
        // （isClose 只在循环顶部检查）。所以必须先把"可能漏出的那一枚"收走，才能断言之后不再有。
        // 上限是 1，所以漏出的至多一枚由 maxTokenCount 与循环顶部检查共同保证。
        Awaits.awaitUntil(bucket::tryAcquire, 200);

        assertFalse(Awaits.awaitUntil(bucket::tryAcquire, 300),
                "漏出的那枚收走之后不应再有：20ms 节拍 × 300ms 窗口 = 若 close 无效至少还会有 10 枚");
    }

    @Test
    @DisplayName("上限是真的：连续多拍不取币，累积也不会超过 maxTokenCount")
    void capHoldsAcrossMultipleTicksWithoutConsumption() {
        // neverExceedsMaxTokenCount 那条钉不住上限：桶被边补边取，任何时刻都只有一枚，
        // 把 :41 的 tc >= maxTokenCount 改成 false 也不会多出一枚可取。这条先把币攒起来再看突发。
        // 这里的 Awaits(awaitUntil(恒假, 250ms)) 只当"经过两个节拍"的计时窗用，不是在猜计数：
        // 断言是上界，多等只会更安全；随后的突发取币只需微秒，远小于 100ms 节拍。
        TickBasedTokenBucket bucket = new TickBasedTokenBucket(1, Duration.ofMillis(100));
        assertFalse(Awaits.awaitUntil(() -> false, 250), "这段窗口只做等待，不取币");

        assertTrue(bucket.tryAcquire(), "两个节拍后应有币可取");
        assertFalse(bucket.tryAcquire(), "上限 1：第二枚只能等下一拍，攒不下");
    }

    @Test
    void closeIsIdempotent() {
        TickBasedTokenBucket bucket = new TickBasedTokenBucket(1, Duration.ofHours(1));
        assertDoesNotThrow(bucket::close);
        assertDoesNotThrow(bucket::close);
    }
}
