package top.turboweb.http.orchestrator.writer;

import org.junit.jupiter.api.Test;
import top.turboweb.http.connection.HttpConnection;
import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.core.StatusLine;
import top.turboweb.http.orchestrator.ExceptionMessageHelper;
import top.turboweb.http.server.constant.ResourceNames;
import top.turboweb.http.testkit.RecordingHttpConnection;
import top.turboweb.http.testkit.Stubs;
import top.turboweb.server.runtime.ResourceManager;
import top.turboweb.server.runtime.ResourceProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ResponseMessageWriter} 的行为锚：Content-Type 门禁与三区间路由次序
 * （未携带 Content-Type 的响应等同找不到处理器直接降级、字节消息直认写出器内建字节写出方法、
 * 订阅声明先查内置策略组再查自定义策略组、
 * 其余消息一律等同找不到处理器而降级写出 500 报错页）、
 * 字节写出方法的三步写出与回调契约（Content-Length 补齐口径、失败链经回调上报且不再写结束标记）、
 * 构造期自定义登记规则（同类重复拒登、null 列表与 null 元素拒登、
 * 构造即经 init 注入资源提供者）、
 * 降级报错页的消息头继承（原响应非实体头按序继承，实体头剔除且头名匹配大小写不敏感）。
 */
class ResponseMessageWriterTest {

    /** 订阅声明消息夹具：实现 WriteHandlerSubscribe，把选择结果钉到指定处理器类 */
    private static final class SubscribedMessage extends HttpResponseMessage<Object> implements WriteHandlerSubscribe {

        private final Class<? extends MessageWriteHandler> subscriberType;

        SubscribedMessage(Class<? extends MessageWriteHandler> subscriberType, Object content) {
            super(StatusLine.OK, content);
            // 携带 Content-Type 通过写出处理器的消息头门禁，使路由聚焦于订阅声明本身
            setHeader("Content-Type", "application/json");
            this.subscriberType = subscriberType;
        }

        @Override
        public Class<? extends MessageWriteHandler> subscriber() {
            return subscriberType;
        }
    }

    /** 自定义处理器夹具：不写出内容，仅置写出标记并捕获收到的资源提供者 */
    private static final class MarkingHandler implements MessageWriteHandler {

        private final AtomicBoolean written = new AtomicBoolean(false);
        private ResourceProvider provider;

        @Override
        public void write(HttpConnection connection, HttpResponseMessage<?> message, Callback callback) {
            written.set(true);
            callback.onFinally(null, CallbackType.FINISHED);
        }

        @Override
        public void init(ResourceProvider resourceProvider) {
            this.provider = resourceProvider;
        }

        boolean wasWritten() {
            return written.get();
        }

        ResourceProvider injectedProvider() {
            return provider;
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

    /** 消息体写出必失败的连接替身 */
    private static final class FailingWriteConnection extends RecordingHttpConnection {

        @Override
        public CompletableFuture<Void> write(byte[] bytes) {
            return CompletableFuture.failedFuture(new IllegalStateException("write failed"));
        }
    }

    /** 写出响应头即同步抛错的连接替身 */
    private static final class ThrowingHeadersConnection extends RecordingHttpConnection {

        @Override
        public CompletableFuture<Void> writeHeaders(StatusLine statusLine, Map<String, List<String>> headers) {
            throw new IllegalStateException("sync failure on headers");
        }
    }

    /** 记录写出操作次序且响应头完成时点可控的连接替身：用于钉住三步写出的先后次序 */
    private static final class OrderedConnection extends RecordingHttpConnection {

        private final List<String> operations = new ArrayList<>();
        private CompletableFuture<Void> headersFuture = new CompletableFuture<>();

        @Override
        public CompletableFuture<Void> writeHeaders(StatusLine statusLine, Map<String, List<String>> headers) {
            operations.add("headers");
            return headersFuture;
        }

        @Override
        public CompletableFuture<Void> write(byte[] bytes) {
            operations.add("body");
            return super.write(bytes);
        }

        @Override
        public CompletableFuture<Void> finishWrite() {
            operations.add("finish");
            return super.finishWrite();
        }

        /** 放行响应头写出完成 */
        void completeHeaders() {
            headersFuture.complete(null);
        }

        /** 使响应头写出失败 */
        void failHeaders(Throwable cause) {
            headersFuture.completeExceptionally(cause);
        }

        List<String> operations() {
            return operations;
        }
    }

    /** 构造携带 Content-Type 的字节消息夹具：写出处理器要求响应自带内容类型 */
    private static ByteResponseMessage byteMessage(byte[] content) {
        ByteResponseMessage message = new ByteResponseMessage(StatusLine.OK, content);
        message.setHeader("Content-Type", "application/octet-stream");
        return message;
    }

    @Test
    void byteMessageIsWrittenByInternalByteWrite() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        ByteResponseMessage message = byteMessage("ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, message, callback);

        assertEquals(1, callback.count(), "回调恰好一次");
        assertNull(callback.error(), "写出成功无错误");
        assertEquals(MessageWriteHandler.CallbackType.FINISHED, callback.type());
        assertEquals(200, connection.writtenStatusLine().code(), "字节消息直认区间一，按消息原样写出");
        assertEquals(List.of("ok"), connection.sentMessages());
        assertEquals(1, connection.finishCount());
    }

    @Test
    void byteMessageKeepsExistingContentLength() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        ByteResponseMessage message = byteMessage("ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        message.setHeader("Content-Length", "99");
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, message, callback);

        assertEquals("99", connection.writtenHeaders().get("Content-Length").getFirst(), "已携带 Content-Length 时不覆写");
    }

    @Test
    void byteMessageWithEmptyContentSkipsBodyWriteButStillFinishes() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        ByteResponseMessage message = byteMessage(new byte[0]);
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, message, callback);

        assertTrue(connection.sentMessages().isEmpty(), "空消息体跳过消息体写出");
        assertEquals("0", connection.writtenHeaders().get("Content-Length").getFirst(), "空消息体补齐 Content-Length 为 0");
        assertEquals(1, connection.finishCount(), "空消息体仍写结束标记");
        assertEquals(1, callback.count());
        assertNull(callback.error());
    }

    @Test
    void subscribedMessageHitsCustomHandler() {
        MarkingHandler handler = new MarkingHandler();
        RecordingHttpConnection connection = new RecordingHttpConnection();
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of(handler))
                .write(connection, new SubscribedMessage(MarkingHandler.class, new Object()), callback);

        assertTrue(handler.wasWritten(), "订阅声明的处理器命中自定义策略组");
        assertEquals(1, callback.count());
        assertNull(callback.error());
        assertNull(connection.writtenStatusLine(), "自定义处理器接管写出，降级报错不得写出");
        assertTrue(connection.sentMessages().isEmpty());
    }

    @Test
    void unregisteredSubscriberFallsBackToErrorPage() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, new SubscribedMessage(MarkingHandler.class, new Object()), callback);

        assertErrorPageWritten(connection, callback, ExceptionMessageHelper.unsupportedWriteHandlerMessage(),
                "声明了写出职责但两级策略组均未登记，降级写出报错页");
    }

    @Test
    void messageWithoutDeclaredTypeFallsBackToErrorPage() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        HttpResponseMessage<String> message = new HttpResponseMessage<>(StatusLine.OK, "text");
        message.setHeader("Content-Type", "text/plain");
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, message, callback);

        assertErrorPageWritten(connection, callback, ExceptionMessageHelper.unsupportedWriteHandlerMessage(),
                "未实现订阅声明的消息等同找不到处理器");
    }

    @Test
    void nullSubscriberDeclarationFallsBackToErrorPage() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        SubscribedMessage message = new SubscribedMessage(null, new Object());
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, message, callback);

        assertErrorPageWritten(connection, callback, ExceptionMessageHelper.unsupportedWriteHandlerMessage(),
                "未声明订阅者类型等同找不到处理器");
    }

    @Test
    void messageWithoutContentTypeFallsBackToErrorPage() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, new HttpResponseMessage<>(StatusLine.OK, "text"), callback);

        assertErrorPageWritten(connection, callback, ExceptionMessageHelper.missingContentTypeMessage(),
                "未携带 Content-Type 的响应等同找不到处理器，降级写出报错页");
    }

    @Test
    void byteMessageWithoutContentTypeFallsBackToErrorPage() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, new ByteResponseMessage(StatusLine.OK, "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8)), callback);

        assertErrorPageWritten(connection, callback, ExceptionMessageHelper.missingContentTypeMessage(),
                "字节消息同样受 Content-Type 门禁约束");
    }

    @Test
    void fallbackCarriesNonEntityHeadersFromOriginalMessage() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        HttpResponseMessage<String> message = new HttpResponseMessage<>(StatusLine.OK, "text");
        message.setHeader("Access-Control-Allow-Origin", "https://example.com");
        message.addHeader("Set-Cookie", "a=1");
        message.addHeader("Set-Cookie", "b=2");
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, message, callback);

        assertErrorPageWritten(connection, callback, ExceptionMessageHelper.missingContentTypeMessage(),
                "未携带 Content-Type 的响应降级写出报错页");
        assertEquals("https://example.com", connection.writtenHeaders().get("Access-Control-Allow-Origin").getFirst(),
                "原响应的非实体头（如 CORS 声明）继承到降级报错页");
        assertEquals(List.of("a=1", "b=2"), connection.writtenHeaders().get("Set-Cookie"),
                "同名多值的非实体头按出现顺序完整继承");
    }

    @Test
    void fallbackClearsEntityHeadersFromOriginalMessage() {
        RecordingHttpConnection connection = new RecordingHttpConnection();
        HttpResponseMessage<String> message = new HttpResponseMessage<>(StatusLine.OK, "text");
        message.setHeader("Content-Type", "application/json");
        message.setHeader("Content-Length", "999");
        message.setHeader("CONTENT-ENCODING", "gzip");
        message.setHeader("transfer-encoding", "chunked");
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, message, callback);

        assertErrorPageWritten(connection, callback, ExceptionMessageHelper.unsupportedWriteHandlerMessage(),
                "降级写出报错页");
        assertFalse(connection.writtenHeaders().containsKey("Content-Encoding"),
                "描述原消息体的实体头不得继承，头名匹配大小写不敏感");
        assertFalse(connection.writtenHeaders().containsKey("Transfer-Encoding"),
                "描述原消息体的实体头不得继承，头名匹配大小写不敏感");
    }

    @Test
    void bodyWriteFailureReportsErrorAndSkipsFinish() {
        FailingWriteConnection connection = new FailingWriteConnection();
        ByteResponseMessage message = byteMessage("ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, message, callback);

        assertEquals(1, callback.count(), "回调恰好一次");
        assertInstanceOf(IllegalStateException.class, callback.error(), "异步写出失败以原异常上报");
        assertEquals("write failed", callback.error().getMessage());
        assertEquals(MessageWriteHandler.CallbackType.FINISHED, callback.type());
        assertEquals(0, connection.finishCount(), "失败链不再写结束标记");
    }

    @Test
    void syncHeadersThrowReportsViaCallback() {
        ThrowingHeadersConnection connection = new ThrowingHeadersConnection();
        ByteResponseMessage message = byteMessage("ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, message, callback);

        assertEquals(1, callback.count(), "写出通道同步抛错也经回调恰好上报一次");
        assertEquals("sync failure on headers", callback.error().getMessage());
        assertEquals(MessageWriteHandler.CallbackType.FINISHED, callback.type());
        assertEquals(0, connection.finishCount());
    }

    @Test
    void bodyWriteWaitsForHeadersCompletion() {
        OrderedConnection connection = new OrderedConnection();
        ByteResponseMessage message = byteMessage("ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, message, callback);

        assertEquals(List.of("headers"), connection.operations(), "响应头未完成前不得发出消息体");
        assertTrue(connection.sentMessages().isEmpty(), "响应头未完成前消息体写出不得被调用");
        assertEquals(0, connection.finishCount(), "响应头未完成前不得写结束标记");

        connection.completeHeaders();

        assertEquals(List.of("headers", "body", "finish"), connection.operations(),
                "三步写出按消息头、消息体、结束标记的次序推进");
        assertEquals(1, callback.count(), "回调恰好一次");
        assertNull(callback.error());
        assertEquals(1, connection.finishCount());
    }

    @Test
    void failedHeadersSkipBodyWrite() {
        OrderedConnection connection = new OrderedConnection();
        ByteResponseMessage message = byteMessage("ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CallbackTrap callback = new CallbackTrap();

        new ResponseMessageWriter(resourceManagerOf(), List.of())
                .write(connection, message, callback);

        connection.failHeaders(new IllegalStateException("headers failed"));

        assertTrue(connection.sentMessages().isEmpty(), "响应头写出失败后不得发出消息体");
        assertEquals(0, connection.finishCount(), "响应头写出失败后不得写结束标记");
        assertEquals(1, callback.count(), "回调恰好一次");
        assertInstanceOf(IllegalStateException.class, callback.error());
    }

    @Test
    void constructorRejectsDuplicateHandlerClass() {
        // 重复判定按处理器类进行：同类不同实例同样拒登
        IllegalStateException again = assertThrows(IllegalStateException.class,
                () -> new ResponseMessageWriter(resourceManagerOf(),
                        List.of(new MarkingHandler(), new MarkingHandler())));
        assertEquals("Duplicate message write handler registration: " + MarkingHandler.class.getName(),
                again.getMessage());
    }

    @Test
    void constructorInjectsProviderIntoCustomHandlers() {
        ResourceProvider provider = resourceManagerOf();
        MarkingHandler handler = new MarkingHandler();

        new ResponseMessageWriter(provider, List.of(handler));

        assertSame(provider, handler.injectedProvider(), "构造期必须把资源提供者交给自定义处理器");
    }

    @Test
    void constructorRejectsNullHandlerElement() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new ResponseMessageWriter(resourceManagerOf(), Arrays.asList((MessageWriteHandler) null)));
        assertEquals("The message write handler must not be null", thrown.getMessage());
    }

    @Test
    void constructorRejectsNullHandlerList() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new ResponseMessageWriter(resourceManagerOf(), null));
        assertEquals("The custom handler list must not be null", thrown.getMessage());
    }

    @Test
    void constructorRejectsNullResourceProvider() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new ResponseMessageWriter(null, List.of()));
        assertEquals("The resource provider must not be null", thrown.getMessage());
    }

    /** 断言降级写出：500 状态行、给定报错页文本与配套响应头、回调以成功 FINISHED 上报 */
    private static void assertErrorPageWritten(RecordingHttpConnection connection, CallbackTrap callback,
                                               String page, String reason) {
        assertEquals(1, callback.count(), reason + "：回调恰好一次");
        assertNull(callback.error(), reason + "：报错页写出成功无错误");
        assertEquals(MessageWriteHandler.CallbackType.FINISHED, callback.type());
        assertEquals(500, connection.writtenStatusLine().code(), reason + "：降级区间写出 500 报错页");
        assertEquals(page, connection.sentMessages().getFirst(),
                reason + "：报错页文本由异常信息 Helper 统一承载");
        assertEquals("text/html;charset=utf-8", connection.writtenHeaders().get("Content-Type").getFirst());
        assertEquals(String.valueOf(page.getBytes(java.nio.charset.StandardCharsets.UTF_8).length),
                connection.writtenHeaders().get("Content-Length").getFirst(),
                reason + "：报错页同样按 UTF-8 字节数补齐 Content-Length");
        assertEquals(1, connection.finishCount(), reason + "：报错页写结束标记");
    }

    /**
     * 构造测试用资源管理器：以内置空间登记磁盘IO执行器资源，
     * 供写出器按 {@link ResourceNames#DISK_IO_THREAD_POOL} 从其中获取
     */
    private static ResourceProvider resourceManagerOf() {
        Map<String, AutoCloseable> builtins = new LinkedHashMap<>();
        builtins.put(ResourceNames.DISK_IO_THREAD_POOL, Stubs.noopExecutor());
        return new ResourceManager.Builder().build(builtins);
    }
}
