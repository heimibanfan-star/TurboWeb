package top.turboweb.http.server.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WriteThrottleChannelHandler} 的背压节流行为锚：
 * 通道可写时出站消息与冲刷事件即时放行，不可写期间一律暂扣挂起，
 * 可写恢复时按到达次序放行并统一冲刷；断连时暂扣消息释放、凭据以失败收场。
 * <p>通道可写状态经打桩的上下文驱动，暂扣凭据取自真实通道，断言其在暂扣期间悬置。</p>
 */
class WriteThrottleChannelHandlerTest {

    /** 打桩的通道上下文：记录放行的写出与冲刷，通道可写状态用例内可控 */
    private static final class RecordingContext {

        /** 通道当前的可写状态 */
        boolean writable = true;
        /** 放行的出站消息，按放行次序 */
        final List<Object> written = new ArrayList<>();
        /** 放行的冲刷次数 */
        int flushCount;
        /** 可写状态变化事件透传次数 */
        int writabilityFired;
        /** 失活事件透传次数 */
        int inactiveFired;

        /** 放行凭据取自该真实通道 */
        final EmbeddedChannel promiseChannel = new EmbeddedChannel();

        private final Channel channelProxy = (Channel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Channel.class},
                (proxy, method, args) -> {
                    if ("isWritable".equals(method.getName())) {
                        return writable;
                    }
                    return defaultReturn(method);
                });

        private final ChannelHandlerContext ctx = (ChannelHandlerContext) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ChannelHandlerContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "channel":
                            return channelProxy;
                        case "write":
                            written.add(args[0]);
                            return null;
                        case "flush":
                            flushCount++;
                            return null;
                        case "fireChannelWritabilityChanged":
                            writabilityFired++;
                            return proxy;
                        case "fireChannelInactive":
                            inactiveFired++;
                            return proxy;
                        default:
                            return defaultReturn(method);
                    }
                });

        private Object defaultReturn(java.lang.reflect.Method method) {
            Class<?> rt = method.getReturnType();
            if (rt == boolean.class) {
                return false;
            }
            return null;
        }

        /** 建立随行真实凭据 */
        ChannelPromise newPromise() {
            return promiseChannel.newPromise();
        }
    }

    // ---------- 可写直放 ----------

    @Test
    void writePassesThroughWhenWritable() throws Exception {
        RecordingContext recording = new RecordingContext();
        WriteThrottleChannelHandler handler = new WriteThrottleChannelHandler();
        Object message = new Object();
        ChannelPromise promise = recording.newPromise();

        handler.write(recording.ctx, message, promise);

        assertSame(message, recording.written.getFirst(), "可写时出站消息即时放行");
    }

    @Test
    void flushPassesThroughWhenWritable() throws Exception {
        RecordingContext recording = new RecordingContext();
        WriteThrottleChannelHandler handler = new WriteThrottleChannelHandler();

        handler.flush(recording.ctx);

        assertEquals(1, recording.flushCount, "可写时冲刷事件即时放行");
    }

    // ---------- 不可写暂扣与恢复放行 ----------

    @Test
    void writeIsHeldWhileUnwritableAndReleasedInOrderOnRestore() throws Exception {
        RecordingContext recording = new RecordingContext();
        WriteThrottleChannelHandler handler = new WriteThrottleChannelHandler();
        recording.writable = false;
        ChannelPromise firstPromise = recording.newPromise();
        ChannelPromise secondPromise = recording.newPromise();

        handler.write(recording.ctx, "first", firstPromise);
        handler.write(recording.ctx, "second", secondPromise);

        assertTrue(recording.written.isEmpty(), "不可写期间出站消息暂扣不放行");
        assertFalse(firstPromise.isDone(), "暂扣期间写出凭据不完成");

        recording.writable = true;
        handler.channelWritabilityChanged(recording.ctx);

        assertEquals(List.of("first", "second"), recording.written, "可写恢复时按到达次序放行");
        assertEquals(1, recording.flushCount, "放行后统一冲刷");
        assertEquals(1, recording.writabilityFired, "可写状态变化事件透传");
        recording.promiseChannel.finishAndReleaseAll();
    }

    @Test
    void flushEventIsDeferredWhileUnwritableAndFlushedOnRestore() throws Exception {
        RecordingContext recording = new RecordingContext();
        WriteThrottleChannelHandler handler = new WriteThrottleChannelHandler();
        recording.writable = false;

        handler.flush(recording.ctx);
        assertEquals(0, recording.flushCount, "不可写期间冲刷事件挂起不放行");

        // 无暂扣消息时挂起的冲刷仍随可写恢复兑现
        recording.writable = true;
        handler.channelWritabilityChanged(recording.ctx);

        assertEquals(1, recording.flushCount, "挂起的冲刷随可写恢复兑现");
    }

    @Test
    void writabilityLostFiresThroughWithoutAction() throws Exception {
        RecordingContext recording = new RecordingContext();
        WriteThrottleChannelHandler handler = new WriteThrottleChannelHandler();

        recording.writable = false;
        handler.channelWritabilityChanged(recording.ctx);

        assertTrue(recording.written.isEmpty(), "转不可写不放行任何消息");
        assertEquals(0, recording.flushCount, "转不可写不冲刷");
        assertEquals(1, recording.writabilityFired, "可写状态变化事件透传");
    }

    // ---------- 断连收场 ----------

    @Test
    void channelInactiveReleasesHeldContentAndFailsPromises() throws Exception {
        RecordingContext recording = new RecordingContext();
        WriteThrottleChannelHandler handler = new WriteThrottleChannelHandler();
        recording.writable = false;
        DefaultHttpContent content = new DefaultHttpContent(
                Unpooled.copiedBuffer("held", StandardCharsets.UTF_8));
        ChannelPromise promise = recording.newPromise();
        handler.write(recording.ctx, content, promise);

        handler.channelInactive(recording.ctx);

        assertEquals(0, content.refCnt(), "暂扣消息随失活按引用计数释放");
        assertTrue(promise.isDone() && !promise.isSuccess(), "暂扣写出凭据以失败收场");
        assertEquals(1, recording.inactiveFired, "失活事件透传");
        assertEquals(0, recording.flushCount, "断连收场不冲刷");
    }
}
