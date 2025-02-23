package org.turbo.web.lock;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 锁实例
 */
public class Locks {

    /**
     * session操作的读写锁
     */
    public static final ReentrantReadWriteLock SESSION_LOCK = new ReentrantReadWriteLock();
}
