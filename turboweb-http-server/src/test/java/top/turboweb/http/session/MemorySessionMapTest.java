package top.turboweb.http.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MemorySessionMap} 的属性级过期、会话级过期与并发可见性。
 *
 * <p>过期判定不靠 sleep 推进时钟：属性过期用负 timeout 构造“过期时间戳落在过去”，
 * 会话级过期用注入极端 maxNotUseTime（-1 / Long.MAX_VALUE）把判定变成确定量。
 */
class MemorySessionMapTest {

    @Test
    @DisplayName("永久属性可读，负过期时间的属性立即不可见")
    void permanentAttributeIsVisibleAndExpiredAttributeIsNot() {
        MemorySessionMap map = new MemorySessionMap();
        map.setAttr("keep", "v");
        map.setAttr("gone", "v", -1L);

        assertEquals("v", map.getAttr("keep"));
        assertNull(map.getAttr("gone"), "过期时间戳落在过去，判定必然为已过期");
    }

    @Test
    @DisplayName("钉住 HS-8：类型化读取做强制转换，类型不符抛 ClassCastException")
    void typedGetCastsAndThrowsOnTypeMismatch() {
        MemorySessionMap map = new MemorySessionMap();
        map.setAttr("text", "hello");

        assertEquals("hello", map.getAttr("text", String.class));
        assertNull(map.getAttr("missing", String.class), "属性不存在返回 null");
        // 与 javadoc 不符（HS-8，非阻塞）：HttpSessionStore.getAttr(key, clazz) 的注释写
        // “转换失败返回 null”，实现却是 clazz.cast，类型不符会抛 ClassCastException。
        // 本用例钉住真实行为；若将来修正，本用例应改为期望正确行为（转换失败返回 null ⇒ assertNull）。
        assertThrows(ClassCastException.class, () -> map.getAttr("text", Integer.class));
    }

    @Test
    @DisplayName("remAttr 只删除指定属性")
    void remAttrRemovesOnlyTheGivenKey() {
        MemorySessionMap map = new MemorySessionMap();
        map.setAttr("a", 1);
        map.setAttr("b", 2);
        map.remAttr("a");

        assertNull(map.getAttr("a"));
        assertEquals(2, map.getAttr("b"));
    }

    @Test
    @DisplayName("timeoutValGC 只清掉已过期属性，保留永久属性")
    void timeoutValGCRemovesExpiredButKeepsPermanent() {
        MemorySessionMap map = new MemorySessionMap();
        map.setAttr("keep", "v");
        map.setAttr("gone", "v", -1L);

        map.timeoutValGC();

        assertNull(map.getAttr("gone"));
        assertEquals("v", map.getAttr("keep"));
    }

    @Test
    @DisplayName("会话级过期判定：maxNotUseTime 注入 -1 必过期、Long.MAX_VALUE 必不过期")
    void sessionTimeoutDecisionIsDeterministicWithExtremeThresholds() {
        MemorySessionMap map = new MemorySessionMap();
        // 构造刚发生，lastUseTime ≈ now，差值 >= 0。注入极端阈值把“有没有过期”变成确定量，不 sleep。
        assertTrue(map.isTimeout(-1L), "差值(>=0) > -1 恒成立");
        assertFalse(map.isTimeout(Long.MAX_VALUE), "差值是毫秒级，不可能超过 Long.MAX_VALUE");
    }

    @Test
    @DisplayName("expireAt 把最后使用时间刷新到当前时刻")
    void expireAtRefreshesLastUseTime() throws Exception {
        MemorySessionMap map = new MemorySessionMap();
        setLastUseTime(map, System.currentTimeMillis() - 10_000L);
        assertTrue(map.isTimeout(1_000L), "把最后使用时间拨回 10 秒前，1 秒的未用阈值已被超过");

        map.expireAt();

        assertFalse(map.isTimeout(1_000L), "续时后未用时长归零，不再过期");
    }

    @Test
    @DisplayName("并发写入全部可见，共享键最终值必来自某个写入者（只断言不变式）")
    void concurrentWritesAreAllVisible() throws Exception {
        MemorySessionMap map = new MemorySessionMap();
        int threads = 8;
        int perThread = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<String> writtenSharedValues = ConcurrentHashMap.newKeySet();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                final int thread = t;
                pool.execute(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            map.setAttr("t" + thread + "-" + i, i);
                            String value = "s" + thread;
                            writtenSharedValues.add(value);
                            map.setAttr("shared", value);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "所有写线程应在超时内结束");
        }

        // 不变式 1：每个被写入的键都能读回它自己的值（已接受的不丢 + 跨线程可见性）
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < perThread; i++) {
                assertEquals(i, map.getAttr("t" + t + "-" + i),
                        "键 t" + t + "-" + i + " 的写入丢失或不可见");
            }
        }
        // 不变式 2：共享键的最终值必是某个写入者写过的值（不是 null、不是撕裂值）
        Object shared = map.getAttr("shared");
        assertNotNull(shared);
        assertTrue(writtenSharedValues.contains(shared), "共享键最终值必须来自某个写入者");
    }

    /** 反射把私有 lastUseTime 拨回过去，用于构造“已过期”的确定态（不 sleep 等时钟）。 */
    private static void setLastUseTime(MemorySessionMap map, long value) throws Exception {
        Field field = MemorySessionMap.class.getDeclaredField("lastUseTime");
        field.setAccessible(true);
        field.setLong(map, value);
    }
}
