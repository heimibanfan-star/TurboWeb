package top.turboweb.core.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 连接限流器
 */
public class ConnectLimiter extends ChannelInboundHandlerAdapter {

    private interface Counter {

        /**
         * 增加计数
         *
         * @return 是否成功增加
         */
        boolean increase();

        /**
         * 减少计数
         */
        void decrease();
    }

    private static class NoLockCounter implements Counter {

        private final int maxCount;
        private int count;

        public NoLockCounter(int maxCount) {
            this.maxCount = maxCount;
        }

        @Override
        public boolean increase() {
            if (count < maxCount) {
                count++;
                return true;
            }
            return false;
        }

        @Override
        public void decrease() {
            count--;
        }
    }

    private static class CasCounter implements Counter {

        private final int maxCount;
        private final AtomicInteger count = new AtomicInteger(0);

        public CasCounter(int maxCount) {
            this.maxCount = maxCount;
        }

        @Override
        public boolean increase() {
            if (count.get() >= maxCount) {
                return false;
            }
            return count.getAndIncrement() >= maxCount;
        }

        @Override
        public void decrease() {
            count.decrementAndGet();
        }
    }

    private static class LockCounter implements Counter {

        private final int maxCount;
        private int count = 0;
        private final Lock lock = new ReentrantLock();

        public LockCounter(int maxCount) {
            this.maxCount = maxCount;
        }

        @Override
        public boolean increase() {
            if (count >= maxCount) {
                return false;
            }
            lock.lock();
            try {
                if (count >= maxCount) {
                    return false;
                }
                count++;
                return true;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void decrease() {
            lock.lock();
            try {
                count--;
            } finally {
                lock.unlock();
            }
        }
    }

    private final Counter counter;

    public ConnectLimiter(int maxConnect, int ioThreadNum, int cpuNum) {
        if (ioThreadNum < 1 || cpuNum < 1) {
            throw new IllegalArgumentException("ioThreadNum and cpuNum must be greater than 1");
        }
        if (ioThreadNum == 1) {
            counter = new NoLockCounter(maxConnect);
        } else if (ioThreadNum <= cpuNum) {
            counter = new CasCounter(maxConnect);
        } else {
            counter = new LockCounter(maxConnect);
        }
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (!counter.increase()) {
            ctx.close();
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        counter.decrease();
        super.channelInactive(ctx);
    }
}
