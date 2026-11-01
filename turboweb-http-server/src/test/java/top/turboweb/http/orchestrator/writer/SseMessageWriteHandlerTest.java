package top.turboweb.http.orchestrator.writer;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import top.turboweb.http.connection.InternalHttpConnection;
import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.StatusLine;
import top.turboweb.http.response.SseEmitterPlus;
import top.turboweb.http.testkit.RecordingHttpConnection;
import top.turboweb.server.runtime.ChannelAttributeKeys;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SseMessageWriteHandler} 的行为锚：写出按响应首部、逐块事件推进且在途窗口为 1、
 * 结束信号与流错误之后关闭通道而不写结束标记、编码取 Content-Type 的 charset 且缺失或非法降级 UTF-8、
 * 通道关闭时触发发射器登记的关闭监听器、回调以 IGNORE 恰好上报一次、
 * 长生命周期豁免标记落内建连接、非 SSE 消息拒收。
 */
class SseMessageWriteHandlerTest {

    /** 写出时点可控的连接替身：记录操作次序与事件原文，凭据由用例手动兑现 */
    private static final class GatedConnection extends RecordingHttpConnection {

        private final List<String> operations = new ArrayList<>();
        private final List<byte[]> rawBodies = new ArrayList<>();
        private final Deque<CompletableFuture<Void>> writeFutures = new ArrayDeque<>();
        private final CompletableFuture<Void> headersFuture = new CompletableFuture<>();
        private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

        @Override
        public CompletableFuture<Void> writeHeaders(StatusLine statusLine, Map<String, List<String>> headers) {
            operations.add("headers");
            return headersFuture;
        }

        @Override
        public CompletableFuture<Void> write(byte[] bytes) {
            operations.add("body");
            rawBodies.add(bytes.clone());
            CompletableFuture<Void> future = new CompletableFuture<>();
            writeFutures.add(future);
            return future;
        }

        @Override
        public CompletableFuture<Void> close() {
            operations.add("close");
            return closeFuture;
        }

        /** 放行响应头写出完成 */
        void completeHeaders() {
            headersFuture.complete(null);
        }

        /** 兑现最早一笔未决的块写出凭据 */
        void completeOldestWrite() {
            writeFutures.poll().complete(null);
        }

        /** 使最早一笔未决的块写出凭据失败 */
        void failOldestWrite(Throwable cause) {
            writeFutures.poll().completeExceptionally(cause);
        }

        /** 放行通道关闭完成 */
        void completeClose() {
            closeFuture.complete(null);
        }

        List<String> operations() {
            return operations;
        }

        List<byte[]> rawBodies() {
            return rawBodies;
        }
    }

    /** 回调陷阱：记下上报次数、错误与结果阶段 */
    private static final class CallbackTrap implements MessageWriteHandler.Callback {

        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private final AtomicReference<MessageWriteHandler.CallbackType> type = new AtomicReference<>();

        @Override
        public void onFinally(Throwable error, MessageWriteHandler.CallbackType type) {
            count.incrementAndGet();
            this.error.set(error);
            this.type.set(type);
        }

        int count() {
            return count.get();
        }

        Throwable error() {
            return error.get();
        }

        MessageWriteHandler.CallbackType type() {
            return type.get();
        }
    }

    @Test
    void chunksFlowWithInFlightWindowOneAndEndSignalClosesChannel() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        emitter.sendData("a");
        emitter.sendData("b");
        emitter.sendData("c");
        emitter.complete();
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);

        assertEquals(List.of("headers"), connection.operations(), "首部写出完成前不得发出事件块");

        connection.completeHeaders();

        assertEquals(List.of("headers", "body"), connection.operations(), "首部完成后发出首块");

        connection.completeOldestWrite();

        assertEquals(List.of("headers", "body", "body"), connection.operations(), "上一块凭据完成后才发出下一块");

        connection.completeOldestWrite();
        connection.completeOldestWrite();

        assertEquals(List.of("headers", "body", "body", "body", "close"), connection.operations(),
                "结束信号后不再有事件块，通道以关闭收尾");
        assertEquals(0, connection.finishCount(), "长生命周期连接不写结束标记");

        connection.completeClose();

        assertEquals(1, callback.count(), "回调恰好一次");
        assertNull(callback.error());
        assertEquals(MessageWriteHandler.CallbackType.IGNORE, callback.type(),
                "SSE 为长生命周期连接，结果以 IGNORE 上报");
    }

    @Test
    void lateEndSignalAfterSubscriptionStillClosesChannel() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);
        connection.completeHeaders();

        assertEquals(List.of("headers"), connection.operations(), "订阅后流上无事件不发块");

        emitter.sendData("late");

        assertEquals(List.of("headers", "body"), connection.operations(), "订阅后投递的事件照常逐块写出");

        emitter.complete();
        connection.completeOldestWrite();

        assertEquals(List.of("headers", "body", "close"), connection.operations(),
                "订阅后的结束信号仍以关闭通道收尾");
    }

    @Test
    void callbackReportsIgnoreImmediatelyOnce() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        RecordingHttpConnection connection = new RecordingHttpConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);

        assertEquals(1, callback.count(), "长生命周期连接的回调在写出发起时以 IGNORE 恰好上报一次");
        assertNull(callback.error());
        assertEquals(MessageWriteHandler.CallbackType.IGNORE, callback.type());
    }

    @Test
    void charsetFromContentTypeIsApplied() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        emitter.setHeader("Content-Type", "text/event-stream;charset=GBK");
        emitter.sendData("中文");
        emitter.complete();
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);
        connection.completeHeaders();
        connection.completeOldestWrite();
        connection.completeClose();

        assertEquals("data:中文\n\n",
                new String(connection.rawBodies().getFirst(), Charset.forName("GBK")),
                "事件块按 Content-Type 携带的 GBK 编码转字节");
    }

    @Test
    void charsetParamWithSpacesAroundEqualsIsApplied() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        emitter.setHeader("Content-Type", "text/event-stream; charset = GBK");
        emitter.sendData("中文");
        emitter.complete();
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);
        connection.completeHeaders();
        connection.completeOldestWrite();
        connection.completeClose();

        assertEquals("data:中文\n\n",
                new String(connection.rawBodies().getFirst(), Charset.forName("GBK")),
                "charset 参数名与等号周围含空白时仍按 GBK 编码转字节");
    }

    @Test
    void charsetResolvedFromFirstHeaderElement() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        emitter.setHeader("Content-Type", "text/plain;charset=GBK, text/html;charset=UTF-16");
        emitter.sendData("中文");
        emitter.complete();
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);
        connection.completeHeaders();
        connection.completeOldestWrite();
        connection.completeClose();

        assertEquals("data:中文\n\n",
                new String(connection.rawBodies().getFirst(), Charset.forName("GBK")),
                "取值含逗号分隔多元素时取首元素的 charset");
    }

    @Test
    void charsetFallsBackToUtf8WhenMissing() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        emitter.sendData("中文");
        emitter.complete();
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);
        connection.completeHeaders();
        connection.completeOldestWrite();
        connection.completeClose();

        assertEquals("data:中文\n\n",
                new String(connection.rawBodies().getFirst(), StandardCharsets.UTF_8),
                "Content-Type 未携带 charset 时降级 UTF-8");
    }

    @Test
    void charsetFallsBackToUtf8WhenUnknown() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        emitter.setHeader("Content-Type", "text/event-stream;charset=NOT-A-CHARSET");
        emitter.sendData("中文");
        emitter.complete();
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);
        connection.completeHeaders();
        connection.completeOldestWrite();
        connection.completeClose();

        assertEquals("data:中文\n\n",
                new String(connection.rawBodies().getFirst(), StandardCharsets.UTF_8),
                "charset 非法时降级 UTF-8");
    }

    @Test
    void transferEncodingChunkedSetWhenAbsentAndContentLengthRemoved() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        emitter.setHeader("Content-Length", "99");
        RecordingHttpConnection connection = new RecordingHttpConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);

        assertEquals("chunked", connection.writtenHeaders().get("Transfer-Encoding").getFirst(),
                "首部未携带传输编码头时补齐 chunked 分块传输声明");
        assertFalse(connection.writtenHeaders().containsKey("Content-Length"),
                "事件流长度由通道关闭划定，携带的 Content-Length 头写出前剔除");
    }

    @Test
    void transferEncodingKeptWhenPresent() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        emitter.setHeader("Transfer-Encoding", "identity");
        RecordingHttpConnection connection = new RecordingHttpConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);

        assertEquals("identity", connection.writtenHeaders().get("Transfer-Encoding").getFirst(),
                "首部已携带传输编码头时不覆写");
    }

    @Test
    void channelCloseFiresEmitterCloseListeners() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        AtomicBoolean first = new AtomicBoolean(false);
        AtomicBoolean second = new AtomicBoolean(false);
        emitter.addCloseListener(() -> first.set(true));
        emitter.addCloseListener(() -> second.set(true));
        RecordingHttpConnection connection = new RecordingHttpConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);
        connection.fireCloseListeners();

        assertTrue(first.get() && second.get(), "通道关闭事件触发发射器登记的全部关闭监听器");
    }

    @Test
    void writeFailureClosesChannelWithoutSecondReport() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        emitter.sendData("a");
        emitter.sendData("b");
        emitter.complete();
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);
        connection.completeHeaders();
        connection.completeOldestWrite();
        connection.failOldestWrite(new IllegalStateException("write failed"));

        assertEquals(List.of("headers", "body", "body", "close"), connection.operations(),
                "事件块写出失败后不再发出后续块，通道以关闭收尾");
        assertEquals(1, callback.count(), "回调恰好一次，后续失败不重复上报");
        assertEquals(MessageWriteHandler.CallbackType.IGNORE, callback.type());

        connection.completeClose();
    }

    @Test
    void streamErrorClosesChannel() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        emitter.sendData("a");
        emitter.content().tryEmitError(new IllegalStateException("stream error"));
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new SseMessageWriteHandler().write(connection, emitter, callback);
        connection.completeHeaders();
        connection.completeOldestWrite();

        assertEquals(List.of("headers", "body", "close"), connection.operations(),
                "流出错后不再有事件块，通道以关闭收尾");

        connection.completeClose();
    }

    @Test
    void internalConnectionGetsIdleExemptAndCloseListenersWired() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            InternalHttpConnection connection = new InternalHttpConnection(channel);
            SseEmitterPlus emitter = new SseEmitterPlus();
            AtomicBoolean closed = new AtomicBoolean(false);
            emitter.addCloseListener(() -> closed.set(true));
            emitter.sendData("a");
            emitter.complete();
            CallbackTrap callback = new CallbackTrap();

            new SseMessageWriteHandler().write(connection, emitter, callback);
            channel.runPendingTasks();

            assertEquals(Boolean.TRUE, channel.attr(ChannelAttributeKeys.IDLE_EXEMPT).get(),
                    "SSE 启动即落空闲豁免标记");
            assertTrue(closed.get(), "结束信号驱动的通道关闭沿 Netty 关闭事件触发关闭监听器");
            assertEquals(1, callback.count());
            assertEquals(MessageWriteHandler.CallbackType.IGNORE, callback.type());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void nonSseMessageIsRejected() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        ByteResponseMessage message = new ByteResponseMessage(StatusLine.OK, "ok".getBytes(StandardCharsets.UTF_8));
        message.setHeader("Content-Type", "text/event-stream");
        CallbackTrap callback = new CallbackTrap();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new SseMessageWriteHandler().write(connection, message, callback));

        assertTrue(thrown.getMessage().contains(ByteResponseMessage.class.getName()), "非 SSE 消息拒收并报出实际类型");
    }
}
