package top.turboweb.http.orchestrator.writer;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.core.StatusLine;
import top.turboweb.http.response.StreamResponseMessage;
import top.turboweb.http.testkit.RecordingHttpConnection;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
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
 * {@link StreamMessageWriteHandler} 的行为锚：写出按响应首部、逐块响应体、结束标记推进，
 * 上一块写出凭据完成后才发出下一块（在途窗口为 1）、
 * 首部未携带传输编码头时补齐 chunked 且已携带时不覆写、
 * 首部失败、块写出失败、流出错与同步抛错经回调恰好上报一次且不再写结束标记并取消流、
 * 任一失败路径均以关闭通道收尾、
 * 空流仍写结束标记、非流式消息拒收。
 */
class StreamMessageWriteHandlerTest {

    /** 写出时点可控的连接替身：记录写出次序，首部与逐块写出凭据由用例手动兑现 */
    private static final class GatedConnection extends RecordingHttpConnection {

        private final List<String> operations = new ArrayList<>();
        private final Deque<CompletableFuture<Void>> writeFutures = new ArrayDeque<>();
        private final Map<String, List<String>> headerMap = new LinkedHashMap<>();
        private final CompletableFuture<Void> headersFuture = new CompletableFuture<>();
        private final CompletableFuture<Void> finishFuture = new CompletableFuture<>();

        @Override
        public CompletableFuture<Void> writeHeaders(StatusLine statusLine, Map<String, List<String>> headers) {
            operations.add("headers");
            headerMap.putAll(headers);
            return headersFuture;
        }

        @Override
        public CompletableFuture<Void> write(byte[] bytes) {
            operations.add("body:" + new String(bytes, StandardCharsets.UTF_8));
            CompletableFuture<Void> future = new CompletableFuture<>();
            writeFutures.add(future);
            return future;
        }

        @Override
        public CompletableFuture<Void> finishWrite() {
            operations.add("finish");
            return finishFuture;
        }

        /** 放行响应头写出完成 */
        void completeHeaders() {
            headersFuture.complete(null);
        }

        /** 使响应头写出失败 */
        void failHeaders(Throwable cause) {
            headersFuture.completeExceptionally(cause);
        }

        /** 兑现最早一笔未决的块写出凭据 */
        void completeOldestWrite() {
            writeFutures.poll().complete(null);
        }

        /** 使最早一笔未决的块写出凭据失败 */
        void failOldestWrite(Throwable cause) {
            writeFutures.poll().completeExceptionally(cause);
        }

        /** 使结束标记写出失败 */
        void failFinish(Throwable cause) {
            finishFuture.completeExceptionally(cause);
        }

        /** 放行结束标记写出完成 */
        void completeFinish() {
            finishFuture.complete(null);
        }

        List<String> operations() {
            return operations;
        }

        Map<String, List<String>> sentHeaders() {
            return headerMap;
        }
    }

    /** 写出响应头即同步抛错的连接替身 */
    private static final class ThrowingHeadersConnection extends RecordingHttpConnection {

        @Override
        public CompletableFuture<Void> writeHeaders(StatusLine statusLine, Map<String, List<String>> headers) {
            throw new IllegalStateException("sync failure on headers");
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

    /** 构造携带 Content-Type 的流式消息夹具：写出处理器的消息头门禁要求响应自带内容类型 */
    private static StreamResponseMessage streamMessage(Flux<byte[]> content) {
        StreamResponseMessage message = new StreamResponseMessage(StatusLine.OK, content);
        message.setHeader("Content-Type", "text/event-stream");
        return message;
    }

    @Test
    void chunksWriteSequentiallyAfterHeadersWithInFlightWindowOne() {
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection,
                streamMessage(Flux.fromIterable(List.of("a", "b", "c"))
                        .map(chunk -> chunk.getBytes(StandardCharsets.UTF_8))), callback);

        assertEquals(List.of("headers"), connection.operations(), "首部写出完成前不得订阅流出块");
        assertEquals(0, callback.count(), "首部未完成前回调不得上报");

        connection.completeHeaders();

        assertEquals(List.of("headers", "body:a"), connection.operations(), "首部完成后发出首块");
        assertEquals(0, callback.count());

        connection.completeOldestWrite();

        assertEquals(List.of("headers", "body:a", "body:b"), connection.operations(),
                "上一块凭据完成后才发出下一块");

        connection.completeOldestWrite();

        assertEquals(List.of("headers", "body:a", "body:b", "body:c"), connection.operations());

        connection.completeOldestWrite();

        assertEquals(List.of("headers", "body:a", "body:b", "body:c", "finish"), connection.operations(),
                "流正常结束后写结束标记");

        connection.completeFinish();

        assertEquals(1, callback.count(), "回调恰好一次");
        assertNull(callback.error());
        assertEquals(MessageWriteHandler.CallbackType.FINISHED, callback.type());
    }

    @Test
    void emptyStreamWritesHeadersAndFinishOnly() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection, streamMessage(Flux.empty()), callback);

        assertTrue(connection.sentMessages().isEmpty(), "空流不发出任何块");
        assertEquals(1, connection.finishCount(), "空流仍写结束标记");
        assertEquals(1, callback.count(), "回调恰好一次");
        assertNull(callback.error());
        assertEquals(MessageWriteHandler.CallbackType.FINISHED, callback.type());
    }

    @Test
    void transferEncodingChunkedSetWhenAbsent() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection, streamMessage(Flux.empty()), callback);

        assertEquals("chunked", connection.writtenHeaders().get("Transfer-Encoding").getFirst(),
                "首部未携带传输编码头时补齐 chunked 分块传输声明");
    }

    @Test
    void transferEncodingKeptWhenPresent() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        StreamResponseMessage message = streamMessage(Flux.empty());
        message.setHeader("Transfer-Encoding", "identity");
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection, message, callback);

        assertEquals("identity", connection.writtenHeaders().get("Transfer-Encoding").getFirst(),
                "首部已携带传输编码头时不覆写");
    }

    @Test
    void staleContentLengthRemovedBeforeHeadersWrite() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        StreamResponseMessage message = streamMessage(Flux.just("ok".getBytes(StandardCharsets.UTF_8)));
        message.setHeader("Content-Length", "99");
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection, message, callback);

        assertFalse(connection.writtenHeaders().containsKey("Content-Length"),
                "流式长度由终止块划定，携带的 Content-Length 头写出前剔除");
        assertEquals("chunked", connection.writtenHeaders().get("Transfer-Encoding").getFirst());
    }

    @Test
    void writeFailureReportsErrorSkipsFinishAndCancelsUpstream() {
        GatedConnection connection = new GatedConnection();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection,
                streamMessage(Flux.fromIterable(List.of("a", "b", "c"))
                        .map(chunk -> chunk.getBytes(StandardCharsets.UTF_8))
                        .doOnCancel(() -> cancelled.set(true))), callback);
        connection.completeHeaders();
        connection.completeOldestWrite();
        connection.failOldestWrite(new IllegalStateException("write failed"));

        assertEquals(List.of("headers", "body:a", "body:b"), connection.operations(),
                "失败前最后一块已发出，后续块不再发出");
        assertEquals(0, connection.finishCount(), "块写出失败不再写结束标记");
        assertTrue(cancelled.get(), "块写出失败取消上游流");
        assertEquals(1, callback.count(), "回调恰好一次");
        assertInstanceOf(IllegalStateException.class, callback.error());
        assertEquals("write failed", callback.error().getMessage());
        assertEquals(MessageWriteHandler.CallbackType.FINISHED, callback.type());
    }

    @Test
    void writeFailureClosesChannel() {
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection,
                streamMessage(Flux.fromIterable(List.of("a", "b"))
                        .map(chunk -> chunk.getBytes(StandardCharsets.UTF_8))), callback);
        connection.completeHeaders();
        connection.completeOldestWrite();
        connection.failOldestWrite(new IllegalStateException("write failed"));

        assertEquals(1, callback.count(), "回调恰好一次");
        assertEquals(1, connection.closeCount(), "块写出失败后帧序已残缺，通道以关闭收尾");
    }

    @Test
    void headerFailureClosesChannel() {
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection, streamMessage(Flux.empty()), callback);
        connection.failHeaders(new IllegalStateException("headers failed"));

        assertEquals(1, callback.count(), "回调恰好一次");
        assertEquals(1, connection.closeCount(), "首部写出失败后通道以关闭收尾");
    }

    @Test
    void finishFailureClosesChannel() {
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection, streamMessage(Flux.just("a".getBytes(StandardCharsets.UTF_8))), callback);
        connection.completeHeaders();
        connection.completeOldestWrite();
        connection.failFinish(new IllegalStateException("finish failed"));

        assertEquals(1, callback.count(), "回调恰好一次");
        assertEquals(1, connection.closeCount(), "结束标记写出失败后通道以关闭收尾");
    }

    @Test
    void streamErrorClosesChannel() {
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection,
                streamMessage(Flux.error(new IllegalStateException("stream error"))), callback);
        connection.completeHeaders();

        assertEquals(1, callback.count(), "回调恰好一次");
        assertEquals(1, connection.closeCount(), "流出错后响应帧序已残缺，通道以关闭收尾");
    }

    @Test
    void syncHeadersThrowClosesChannel() {
        ThrowingHeadersConnection connection = new ThrowingHeadersConnection();
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection, streamMessage(Flux.empty()), callback);

        assertEquals(1, callback.count(), "回调恰好一次");
        assertEquals(1, connection.closeCount(), "写出通道同步抛错同样以关闭通道收尾");
    }

    @Test
    void headerFailureReportsErrorWithoutBodyOrFinish() {
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection, streamMessage(Flux.just("a".getBytes(StandardCharsets.UTF_8))), callback);
        connection.failHeaders(new IllegalStateException("headers failed"));

        assertEquals(List.of("headers"), connection.operations(), "首部写出失败后不得发出任何块");
        assertEquals(0, connection.finishCount(), "首部写出失败后不得写结束标记");
        assertEquals(1, callback.count(), "回调恰好一次");
        assertInstanceOf(IllegalStateException.class, callback.error());
    }

    @Test
    void finishFailureReportsError() {
        GatedConnection connection = new GatedConnection();
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection, streamMessage(Flux.just("a".getBytes(StandardCharsets.UTF_8))), callback);
        connection.completeHeaders();
        connection.completeOldestWrite();
        connection.failFinish(new IllegalStateException("finish failed"));

        assertEquals(1, callback.count(), "回调恰好一次");
        assertInstanceOf(IllegalStateException.class, callback.error());
        assertEquals(MessageWriteHandler.CallbackType.FINISHED, callback.type());
    }

    @Test
    void syncHeadersThrowReportsViaCallback() {
        ThrowingHeadersConnection connection = new ThrowingHeadersConnection();
        CallbackTrap callback = new CallbackTrap();

        new StreamMessageWriteHandler().write(connection, streamMessage(Flux.empty()), callback);

        assertEquals(1, callback.count(), "写出通道同步抛错也经回调恰好上报一次");
        assertEquals("sync failure on headers", callback.error().getMessage());
        assertEquals(MessageWriteHandler.CallbackType.FINISHED, callback.type());
        assertEquals(0, connection.finishCount());
    }

    @Test
    void nonStreamMessageIsRejected() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        ByteResponseMessage message = new ByteResponseMessage(StatusLine.OK, "ok".getBytes(StandardCharsets.UTF_8));
        message.setHeader("Content-Type", "application/octet-stream");
        CallbackTrap callback = new CallbackTrap();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new StreamMessageWriteHandler().write(connection, message, callback));

        assertTrue(thrown.getMessage().contains(ByteResponseMessage.class.getName()), "非流式消息拒收并报出实际类型");
    }
}
