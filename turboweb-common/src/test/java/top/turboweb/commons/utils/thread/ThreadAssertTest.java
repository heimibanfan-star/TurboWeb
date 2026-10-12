package top.turboweb.commons.utils.thread;

import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线程断言：正例必须在目标线程上取证，反例在调用线程上取证。
 *
 * <p>不用 {@code EmbeddedChannel.eventLoop()}：EmbeddedEventLoop 的 inEventLoop 恒真，
 * 正例就成了自证。这里用真实 NioEventLoopGroup，把断言丢进 loop 的任务队列执行。
 */
class ThreadAssertTest {

    @Test
    void assertIsVirtualThreadPassesOnVirtualThread() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        VirtualThreads.startThread(() -> {
            try {
                ThreadAssert.assertIsVirtualThread();
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                done.countDown();
            }
        }, "probe-thread-assert");
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNull(thrown.get(), "虚拟线程上调用不该抛出");
    }

    @Test
    void assertIsVirtualThreadFailsOnPlatformThread() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                ThreadAssert::assertIsVirtualThread);
        assertEquals("This method must be called from a virtual thread", e.getMessage());
    }

    @Test
    void assertIsEventLoopPassesOnLoopThread() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            EventLoop loop = group.next();
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            loop.execute(() -> {
                try {
                    ThreadAssert.assertIsEventLoop(loop);
                } catch (Throwable t) {
                    thrown.set(t);
                } finally {
                    done.countDown();
                }
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNull(thrown.get(), "事件循环线程上调用不该抛出");
        } finally {
            // 只发起关闭，不断言终止（隔离规则第 7 条）
            group.shutdownGracefully();
        }
    }

    @Test
    void assertIsEventLoopFailsOutsideLoop() {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> ThreadAssert.assertIsEventLoop(group.next()));
            assertEquals("This method must be called from an event loop", e.getMessage());
        } finally {
            group.shutdownGracefully();
        }
    }
}
