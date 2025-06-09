package top.turboweb.commons.senntinels;

import io.netty.channel.EventLoop;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自毁哨兵
 */
public class AutoDestructSentinel implements SchedulerSentinel {

    public static class EnableScheduler {
        private final int initCapacity;
        private final int maxCapacity;

        public EnableScheduler(int initCapacity, int maxCapacity) {
            this.initCapacity = initCapacity;
            this.maxCapacity = maxCapacity;
        }

        public int initCapacity() {
            return initCapacity;
        }

        public int maxCapacity() {
            return maxCapacity;
        }
    }

    public static AttributeKey<SchedulerSentinel> ATTRIBUTE_KEY = AttributeKey.valueOf("AutoDestructSentinel");

    private static final Logger log = LoggerFactory.getLogger(AutoDestructSentinel.class);
    private Runnable[] tasks;
    private final ReentrantLock takeLock = new ReentrantLock();
    private final Condition takeCondition = takeLock.newCondition();
    private int head = 0;
    private int tail = 0;
    private final AtomicInteger count = new AtomicInteger(0);
    private long timeout = 0;
    private volatile boolean isDestruct = true;
    private final ReentrantLock destructLLock = new ReentrantLock();
    private final EventLoop eventLoop;
    private final int maxCapacity;
    private volatile boolean isOverflow = false;
    private volatile int preCapacity;

    public AutoDestructSentinel(int initCapacity, int maxCapacity, EventLoop eventLoop) {
        tasks = new Runnable[initCapacity];
        this.maxCapacity = Math.max(initCapacity, maxCapacity);
        this.preCapacity = initCapacity;
        this.eventLoop = eventLoop;
    }

    @Override
    public boolean submitTask(Runnable runnable) {
        if (eventLoop.inEventLoop()) {
            destructLLock.lock();
            boolean flag;
            try {
                if (isDestruct) {
                    // 判断是否溢出
                    if (isOverflow && this.preCapacity < maxCapacity) {
                        int capacity = this.preCapacity * 2;
                        if (capacity > maxCapacity) {
                            capacity = maxCapacity;
                        }
                        this.preCapacity = capacity;
                        isOverflow = false;
                        tasks = new Runnable[capacity];
                    } else {
                        isOverflow = false;
                        tasks = new Runnable[preCapacity];
                    }
                    flag = offer(runnable);
                    startVirtualThread();
                    isDestruct = false;
                } else {
                    flag = offer(runnable);
                }
            } finally {
                destructLLock.unlock();
            }
            return flag;
        } else {
            return false;
        }
    }

    /**
     * 添加任务
     * @param task 任务
     */
    public boolean offer(Runnable task) {
        // 判断队列是否已满
        if (count.get() == tasks.length) {
            isOverflow = true;
            return false;
        } else {
            tasks[tail] = task;
            tail = (tail + 1) % tasks.length;
            int count = this.count.incrementAndGet();
            if (count <= 1) {
                takeLock.lock();
                try {
                    takeCondition.signal();
                } finally {
                    takeLock.unlock();
                }
            }
        }
        return true;
    }

    /**
     * 开启虚拟线程
     */
    private void startVirtualThread() {
        Thread.ofVirtual().start(this::startAutoDestructSentinel);
    }

    /**
     * 开启自毁哨兵
     */
    private void startAutoDestructSentinel() {
        try {
            while (true) {
                // 判断是否有元素
                if (count.get() > 0) {
                    consumerTask();
                    continue;
                }
                // 加锁重新检测元素
                takeLock.lock();
                try {
                    // 如果有元素，进行下一轮消费
                    if (count.get() > 0) {
                        continue;
                    }
                    // 等待元素的提交
                    boolean await = takeCondition.await(timeout, TimeUnit.NANOSECONDS);
                    // 如果线程没有超时进行下一轮消费
                    if (await) {
                        continue;
                    }
                } catch (InterruptedException ignore) {
                } finally {
                    takeLock.unlock();
                }
                // 抢占锁进行线程的销毁
                destructLLock.lock();
                try {
                    // 判断是否有剩余的元素，重新消费
                    if (count.get() > 0) {
                        continue;
                    }
                    // 销毁队列
                    tasks = null;
                    // 销毁当前线程
                    isDestruct = true;
                    return;
                } finally {
                    destructLLock.unlock();
                }
            }
        } catch (Exception e) {
            // 抢占销毁锁
            destructLLock.lock();
            try {
                // 如果已经销毁忽略
                if (isDestruct || count.get() == 0) {
                    // 销毁队列
                    tasks = null;
                    return;
                }
                // 重启
                startVirtualThread();
                // 设置为启动状态
                isDestruct = false;
            } finally {
                destructLLock.unlock();
            }
        }
    }

    /**
     * 消费任务
     */
    private void consumerTask() {
        // 消费元素
        Runnable task = tasks[head];
        tasks[head] = null;
        head = (head + 1) % tasks.length;
        count.decrementAndGet();
        long start = System.nanoTime();
        task.run();
        long end = System.nanoTime();
        timeout = (end - start) * 8;
        if (timeout < 100_000_000) {
            timeout = 100_000_000;
        } else if (timeout > 2_000_000_000) {
            timeout = 2_000_000_000;
        }
    }
}
