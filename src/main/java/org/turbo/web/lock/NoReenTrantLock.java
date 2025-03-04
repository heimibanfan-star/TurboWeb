package org.turbo.web.lock;

import java.util.HashSet;
import java.util.Set;

/**
 * 不可重入锁
 */
public class NoReenTrantLock <T> {

    public final Set<T> LOCK_KEY = new HashSet<>();
}
