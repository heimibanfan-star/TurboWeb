package top.turboweb.core.channel;

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;


public class TurboWebNioSocketChannel extends NioSocketChannel {

    private final ExecutorService zeroCopyPool;
    private volatile boolean isSuspend = false;
    private final ReentrantLock lock = new ReentrantLock();
    private final Queue<SuspendTask> suspendTasks = new ConcurrentLinkedQueue<>();

    private record SuspendTask(Object task, DefaultChannelPromise channelFuture) {
    }

    public TurboWebNioSocketChannel(Channel parent, SocketChannel socket, ExecutorService pool) {
        super(parent, socket);
        this.zeroCopyPool = pool;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        lock.lock();
        try {
            if (isSuspend) {
                DefaultChannelPromise channelFuture = new DefaultChannelPromise(this);
                suspendTasks.add(new SuspendTask(msg, channelFuture));
                return channelFuture;
            } else if (msg instanceof FileRegion) {
                // 挂起当前连接
                isSuspend = true;
                // 执行零拷贝
                return super.writeAndFlush(msg);
            }
        } finally {
            lock.unlock();
        }
        return super.writeAndFlush(msg);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelFuture write(Object msg) {
        lock.lock();
        try {
            if (isSuspend) {
                DefaultChannelPromise channelFuture = new DefaultChannelPromise(this);
                suspendTasks.add(new SuspendTask(msg, channelFuture));
                return channelFuture;
            } else if (msg instanceof FileRegion) {
                // 挂起当前连接
                isSuspend = true;
                // 执行零拷贝
                return super.write(msg);
            }
        } finally {
            lock.unlock();
        }
        return super.write(msg);
    }

    @Override
    public Channel flush() {
        lock.lock();
        try {
            if (!isSuspend) {
                return super.flush();
            }
        } finally {
            lock.unlock();
        }
        return this;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        // 判断当前任务是否是零拷贝任务
        Object current = in.current();
        if (current instanceof FileRegion) {
            zeroCopyPool.execute(() -> {
                try {
                    super.doWrite(in);
                } catch (Exception e) {
                    this.close();
                } finally {
                    // 恢复剩余的任务
                    activeTasks();
                }
            });
        } else {
            super.doWrite(in);
        }
    }

    private void activeTasks() {
        Thread.ofVirtual().start(() -> {
           for(;;) {
               lock.lock();
               try {
                   if (suspendTasks.isEmpty()) {
                       isSuspend = false;
                       break;
                   }
               } finally {
                   lock.unlock();
               }
               SuspendTask task = suspendTasks.poll();
               if (task != null) {
                   try {
                       Object msg = task.task;
                       if (msg instanceof FileRegion) {
                           executeSuspectTask(task);
                           break;
                       } else {
                           executeSuspectTask(task);
                       }
                   } catch (Exception ignore) {
                   }
               }
           }
        });
    }

    /**
     * 执行挂起的任务
     *
     * @param suspendTask 挂起的任务
     */
    private void executeSuspectTask(SuspendTask suspendTask) {
        ChannelFuture channelFuture = super.writeAndFlush(suspendTask.task);
        channelFuture.addListener(f -> {
            if (f.isSuccess()) {
                suspendTask.channelFuture.setSuccess();
            } else {
                suspendTask.channelFuture.setFailure(f.cause());
            }
        });
    }
}
