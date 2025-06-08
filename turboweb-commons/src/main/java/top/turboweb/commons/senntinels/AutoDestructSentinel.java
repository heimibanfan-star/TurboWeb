package top.turboweb.commons.senntinels;

import io.netty.channel.EventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自毁哨兵
 */
public class AutoDestructSentinel implements SchedulerSentinel {

    private static final Logger log = LoggerFactory.getLogger(AutoDestructSentinel.class);
    private final Runnable[] tasks;
    private final ReentrantLock takeLock = new ReentrantLock();
    private final Condition takeCondition = takeLock.newCondition();
    private int head = 0;
    private int tail = 0;
    private final AtomicInteger count = new AtomicInteger(0);
    private long timeout = 0;
    private volatile boolean isDestruct = true;
    private final ReentrantLock destructLLock = new ReentrantLock();
    private final EventLoop eventLoop;

    public AutoDestructSentinel(int size, EventLoop eventLoop) {
        tasks = new Runnable[size];
        this.eventLoop = eventLoop;
    }

    /**
     * 添加任务
     * @param task 任务
     */
    public boolean offer(Runnable task) {
        // 判断队列是否已满
        if (count.get() == tasks.length) {
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

    @Override
    public boolean submitTask(Runnable runnable) {
        if (eventLoop.inEventLoop()) {
            return newConsumer(runnable);
        } else {
            return false;
        }
    }

    /**
     * 重启消费者
     * @param runnable 任务对象
     * @return 是否成功
     */
    private boolean newConsumer(Runnable runnable) {
        boolean flag;
        destructLLock.lock();
        try {
            flag = offer(runnable);
            if (isDestruct) {
                startVirtualThread();
            }
        } finally {
            destructLLock.unlock();
        }
        return flag;
    }

    private void startVirtualThread() {
        Thread.ofVirtual().start(() -> {
            while (true) {
                // 判断是否有元素
                if (count.get() > 0) {
                    // 消费元素
                    Runnable task = tasks[head];
                    tasks[head] = null;
                    head = (head + 1) % tasks.length;
                    count.decrementAndGet();
                    long start = System.nanoTime();
                    task.run();
                    long end = System.nanoTime();
                    timeout = (end - start) * 8;
                    // 大于2s按照2s算
                    if (timeout > 2_000_000_000) {
                        timeout = 2_000_000_000;
                    }
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
                    // 销毁当前线程
                    isDestruct = true;
                    return;
                } finally {
                    destructLLock.unlock();
                }
            }
        });
    }
}
