package top.turboweb.commons.utils.thread;

import io.netty.channel.EventLoop;

/**
 * 线程断言
 */
public class ThreadAssert {

    /**
     * 断言当前线程是虚拟线程
     */
    public static void assertIsVirtualThread() {
        if (!Thread.currentThread().isVirtual()) {
            throw new IllegalStateException("This method must be called from a virtual thread");
        }
    }

    /**
     * 断言当前线程是事件循环线程
     */
    public static void assertIsEventLoop(EventLoop eventLoop) {
        if (!eventLoop.inEventLoop()) {
            throw new IllegalStateException("This method must be called from an event loop");
        }
    }
}
