package top.turboweb.concurrent.lock;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 分段锁
 */
public class SegmentLock {

    private final ReentrantLock[] LOCKS;
    private final ReentrantLock DEFAULT_LOCK = new ReentrantLock();

    /**
     * 构造分段锁。
     *
     * @param lockNum 分段数量，必须大于 0
     * @throws IllegalArgumentException lockNum 非正时在构造期抛出（fail-fast，不留到首次取锁）
     */
    public SegmentLock(int lockNum) {
        if (lockNum <= 0) {
            throw new IllegalArgumentException("lockNum must be greater than 0");
        }
        LOCKS = new ReentrantLock[lockNum];
        for (int i = 0; i < lockNum; i++) {
            LOCKS[i] = new ReentrantLock();
        }
    }

    /**
     * 获取锁
     *
     * @param key 锁的key
     */
    public void lock(Object key) {
        if (key == null) {
            DEFAULT_LOCK.lock();
            return;
        }
        int index = (key.hashCode() & 0x7fffffff) % LOCKS.length;
        LOCKS[index].lock();
    }

    /**
     * 尝试获取锁
     *
     * @param key 锁的key
     * @return 是否获取成功
     */
    public boolean tryLock(Object key) {
        if (key == null) {
            return DEFAULT_LOCK.tryLock();
        }
        int index = (key.hashCode() & 0x7fffffff) % LOCKS.length;
        return LOCKS[index].tryLock();
    }

    /**
     * 释放锁
     *
     * @param key 锁的key
     */
    public void unlock(Object key) {
        if (key == null) {
            DEFAULT_LOCK.unlock();
            return;
        }
        int index = (key.hashCode() & 0x7fffffff) % LOCKS.length;
        LOCKS[index].unlock();
    }

}
