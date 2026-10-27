package top.turboweb.http.core.response;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.connect.InternalHttpConnection;
import top.turboweb.http.testkit.RecordingHttpConnection;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InternalSseEmitter#initSse()} 的初始化与缓存 drain。
 *
 * <p>通道桩用 JDK 动态代理实现 {@link Channel}（纯内存，非 EmbeddedChannel、非 socket、非 start()）：
 * 只应答 {@code writeAndFlush}，把写出的 {@code DefaultHttpContent} 解码成字符串记下并释放其缓冲，
 * 避免 Netty 泄漏检测告警。真正经 NIO/Epoll 的写出与 closeFuture 注册属阶段 3/4。
 */
class InternalSseEmitterTest {

    private static Channel recordingChannel(List<Object> writes) {
        // write() 会在写出返回的 future 上挂监听，桩必须给出已成功终态的真实 future
        EmbeddedChannel futureChannel = new EmbeddedChannel();
        return (Channel) Proxy.newProxyInstance(
                InternalSseEmitterTest.class.getClassLoader(),
                new Class<?>[]{Channel.class},
                (proxy, method, args) -> {
                    if ("writeAndFlush".equals(method.getName()) && args != null && args.length == 1) {
                        Object msg = args[0];
                        if (msg instanceof DefaultHttpContent content) {
                            writes.add(content.content().toString(CharsetUtil.UTF_8));
                            content.release();
                        } else {
                            writes.add(msg);
                        }
                        return futureChannel.newSucceededFuture();
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    if (rt == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }

    @Test
    void initSseFlushesCachedMessagesInOrderAndInitializes() {
        List<Object> writes = new ArrayList<>();
        InternalHttpConnection session = new InternalHttpConnection(recordingChannel(writes));
        InternalSseEmitter emitter = new InternalSseEmitter(session, 10);

        emitter.sendData("a");
        emitter.sendData("b");
        assertEquals(0, writes.size(), "未初始化前只进缓存，不写出");

        emitter.initSse();

        assertEquals(3, writes.size(), "先写 SSE 响应头（本对象），再按序 drain 两条缓存");
        assertEquals(emitter, writes.get(0));
        assertEquals("data:a\n\n", writes.get(1));
        assertEquals("data:b\n\n", writes.get(2));
        assertTrue(emitter.isInit, "初始化完成后置位");
        assertNull(emitter.messageCache, "初始化后卸载缓存");
        assertNull(emitter.cacheLock, "初始化后卸载缓存锁");
    }

    @Test
    void sendAfterInitBypassesCache() {
        List<Object> writes = new ArrayList<>();
        InternalHttpConnection session = new InternalHttpConnection(recordingChannel(writes));
        InternalSseEmitter emitter = new InternalSseEmitter(session, 10);
        emitter.initSse();
        int before = writes.size();

        emitter.sendData("c");

        assertEquals(before + 1, writes.size());
        assertEquals("data:c\n\n", writes.get(before), "缓存与锁已卸载，仍必须能继续发送");
    }

    @Test
    void initSseWithEmptyCacheWritesOnlyHeader() {
        List<Object> writes = new ArrayList<>();
        InternalHttpConnection session = new InternalHttpConnection(recordingChannel(writes));
        InternalSseEmitter emitter = new InternalSseEmitter(session, 10);

        emitter.initSse();

        assertEquals(1, writes.size());
        assertEquals(emitter, writes.get(0));
        assertTrue(emitter.isInit);
    }

    @Test
    void initSseWithPlainSessionThrowsAndReleasesWriteLock() {
        InternalSseEmitter emitter = new InternalSseEmitter(new RecordingHttpConnection(), 10);

        assertThrows(ClassCastException.class, emitter::initSse);

        assertFalse(emitter.sseLock.isWriteLocked(),
                "cast 失败必须经 finally 释放写锁，否则后续任何 initSse 都永久阻塞");
        assertFalse(emitter.isInit, "失败的初始化不得置 isInit");
    }
}
