package top.turboweb.commons.lock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局会话锁的存在性与公平性（spec §7：本阶段唯一允许短暂持有全局锁的用例）。
 */
class LocksTest {

    /** 字段本身静态类型就是 ReentrantReadWriteLock（Locks.java:13），无需 instanceof 取证类型。 */
    private static final ReentrantReadWriteLock SESSION_LOCK = Locks.SESSION_LOCK;

    @Test
    void sessionLockIsFair() {
        assertTrue(SESSION_LOCK.isFair(), "会话锁构造时传 true：读写两侧都要公平，不允许写者饿死");
    }

    @Test
    void writeAndReadLocksRoundTrip() {
        // commons 测试串行执行且本模块无竞争者，取放一次只为覆盖两个锁视图；不在此断言互斥
        SESSION_LOCK.writeLock().lock();
        try {
            assertTrue(SESSION_LOCK.isWriteLocked());
        } finally {
            SESSION_LOCK.writeLock().unlock();
        }
        SESSION_LOCK.readLock().lock();
        try {
            assertEquals(1, SESSION_LOCK.getReadLockCount());
        } finally {
            SESSION_LOCK.readLock().unlock();
        }
        assertEquals(0, SESSION_LOCK.getReadLockCount(), "释放后读计数必须归零，否则持有者泄漏");
    }
}
