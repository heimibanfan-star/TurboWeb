package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.http.core.connect.InternalHttpConnection;
import top.turboweb.http.core.response.ResponseCall;
import top.turboweb.http.server.constant.ResourceNames;
import top.turboweb.http.testkit.Stubs;
import top.turboweb.server.runtime.ResourceManager;
import top.turboweb.server.runtime.ResourceProvider;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ResponseWriterManager} 的行为锚：三级选择次序
 * （响应经 ResponseCall 声明写出器类型——内置表优先、自定义表其次、默认写出器兜底）、
 * 构造期自定义登记规则（列表按写出器类收集为不可变表：系统内置类型拒登、
 * 同类重复拒登、null 列表与 null 元素拒登、构造即经 initWriter 注入资源提供者、
 * 已初始化实例跨管理器复用被拒），以及装配期资源提供者与列表非空约束。
 * 选择结果经各写出器互异的写出副作用甄别：忽略不可监听且零出站、
 * 默认补齐 Content-Length、自定义只置标记、Reactor 类型在平台线程被虚拟线程契约拒绝并关闭连接。
 */
class ResponseWriterManagerTest {

    /** 声明写出器类型的响应夹具：实现 ResponseCall，把选择结果直接钉到指定写出器类。 */
    private static final class CallResponse extends DefaultHttpResponse implements ResponseCall {

        private final Class<? extends ResponseWriter> writerType;

        CallResponse(Class<? extends ResponseWriter> writerType) {
            super(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            this.writerType = writerType;
        }

        @Override
        public Class<? extends ResponseWriter> getWriterType() {
            return writerType;
        }
    }

    /** 自定义写出器夹具：不写出任何内容，仅置写出标记并捕获收到的资源提供者，用于甄别自定义表命中。 */
    private static final class MarkingWriter extends AbstractResponseWriter {

        private final AtomicBoolean written = new AtomicBoolean(false);
        private ResourceProvider provider;

        @Override
        protected ChannelFuture doWrite(HttpResponse response, InternalHttpConnection session) {
            written.set(true);
            return session.getChannel().newPromise().setSuccess();
        }

        @Override
        public void initWriter(ResourceProvider resourceProvider) {
            super.initWriter(resourceProvider);
            this.provider = resourceProvider;
        }

        MicroTaskExecutor injectedExecutor() {
            return diskIoExecutor;
        }

        ResourceProvider injectedProvider() {
            return provider;
        }

        boolean wasWritten() {
            return written.get();
        }
    }

    @Test
    void declaredInternalWriterTypeHitsInternalTable() {
        EmbeddedChannel channel = new EmbeddedChannel();
        CallResponse response = new CallResponse(IgnoredResponseWriter.class);

        ResponseWriter.WriteResult result = new ResponseWriterManager(resourceManagerOf(Stubs.noopExecutor()), List.of())
                .write(response, new InternalHttpConnection(channel));

        assertEquals(ResponseWriter.WriteType.NOT_LISTENABLE, result.writeType(),
                "内置忽略写出器命中：结果不可监听");
        assertNull(result.channelFuture(), "不可监听结果无写出凭据");
        assertNull(channel.readOutbound(), "默认路径会写出响应帧，此处必须为空");
        channel.finishAndReleaseAll();
    }

    @Test
    void declaredWriterTypeWithoutRegistrationFallsToDefault() {
        EmbeddedChannel channel = new EmbeddedChannel();
        CallResponse response = new CallResponse(MarkingWriter.class);

        ResponseWriter.WriteResult result = new ResponseWriterManager(resourceManagerOf(Stubs.noopExecutor()), List.of())
                .write(response, new InternalHttpConnection(channel));

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        assertTrue(result.channelFuture().isSuccess());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH),
                "声明类型未登记即走默认写出器（补齐 Content-Length）");
        assertSame(response, channel.readOutbound());
        channel.finishAndReleaseAll();
    }

    @Test
    void constructorCustomWriterIsSelectedByDeclaration() {
        EmbeddedChannel channel = new EmbeddedChannel();
        MarkingWriter writer = new MarkingWriter();
        ResponseWriterManager manager = new ResponseWriterManager(resourceManagerOf(Stubs.noopExecutor()), List.of(writer));

        ResponseWriter.WriteResult result = manager.write(
                new CallResponse(MarkingWriter.class), new InternalHttpConnection(channel));

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        assertTrue(result.channelFuture().isSuccess());
        assertTrue(writer.wasWritten(), "声明类型命中构造期登记的自定义写出器");
        assertNull(channel.readOutbound(), "自定义写出器不落任何出站帧，默认路径则会写出响应");
        channel.finishAndReleaseAll();
    }

    @Test
    void responseWithoutCallDeclarationFallsToDefault() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultFullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.content().writeCharSequence("abc", StandardCharsets.UTF_8);

        ResponseWriter.WriteResult result = new ResponseWriterManager(resourceManagerOf(Stubs.noopExecutor()), List.of())
                .write(response, new InternalHttpConnection(channel));

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        assertTrue(result.channelFuture().isSuccess());
        assertEquals("3", response.headers().get(HttpHeaderNames.CONTENT_LENGTH),
                "未实现 ResponseCall 的响应由默认写出器兜底");
        assertSame(response, channel.readOutbound());
        channel.finishAndReleaseAll();
    }

    @Test
    void declaredReactorWriterOnPlatformThreadClosesConnection() {
        // 平台线程直调被虚拟线程契约拒绝，模板兜底关闭连接且不再写出错误页；
        // 默认路径会按 200 原样写出且连接存活，以此甄别声明命中内置表的 Reactor 写出器
        EmbeddedChannel channel = new EmbeddedChannel();
        CallResponse response = new CallResponse(ReactorResponseWriter.class);

        ResponseWriter.WriteResult result = new ResponseWriterManager(resourceManagerOf(Stubs.noopExecutor()), List.of())
                .write(response, new InternalHttpConnection(channel));

        assertEquals(ResponseWriter.WriteType.NOT_LISTENABLE, result.writeType(),
                "写出异常无凭据，结果不可监听");
        assertNull(result.channelFuture(), "不可监听结果的凭据恒为空");
        assertNull(channel.readOutbound(), "兜底不再写出错误页，默认路径则会写出响应");
        channel.runPendingTasks();
        assertFalse(channel.isActive(), "写出异常必须关闭连接");
        channel.finishAndReleaseAll();
    }

    @Test
    void constructorRejectsBuiltinWriterInList() {
        List<ResponseWriter> builtins = List.of(
                new SseResponseWriter(),
                new FileStreamResponseWriter(),
                new DefaultResponseWriter(),
                new ZeroCopyResponseWriter(),
                new ReactorResponseWriter(),
                new IgnoredResponseWriter());
        for (ResponseWriter builtin : builtins) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> new ResponseWriterManager(resourceManagerOf(Stubs.noopExecutor()), List.of(builtin)),
                    builtin.getClass().getSimpleName() + " 为系统内置写出器，不得登记");
            assertEquals("Can not register a built-in response writer: " + builtin.getClass().getName(), thrown.getMessage());
        }
    }

    @Test
    void constructorRejectsDuplicateWriterClass() {
        // 重复判定按写出器类进行：同类不同实例同样拒登
        IllegalStateException again = assertThrows(IllegalStateException.class,
                () -> new ResponseWriterManager(resourceManagerOf(Stubs.noopExecutor()),
                        List.of(new MarkingWriter(), new MarkingWriter())));
        assertEquals("Duplicate response writer registration: " + MarkingWriter.class.getName(), again.getMessage());
    }

    @Test
    void constructorInjectsDiskIoExecutorIntoAllWriters() {
        MicroTaskExecutor executor = Stubs.directExecutor();
        MarkingWriter writer = new MarkingWriter();

        new ResponseWriterManager(resourceManagerOf(executor), List.of(writer));

        assertSame(executor, writer.injectedExecutor(), "构造期必须把磁盘IO执行器注入自定义写出器");
    }

    @Test
    void sameCustomWriterInstanceAcrossTwoManagersIsRejected() {
        // 注入护栏在写出器侧生效：同一实例传入第二个管理器即构造失败, 而非静默沿用首个执行器
        MarkingWriter writer = new MarkingWriter();
        MicroTaskExecutor first = Stubs.noopExecutor();
        new ResponseWriterManager(resourceManagerOf(first), List.of(writer));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new ResponseWriterManager(resourceManagerOf(Stubs.directExecutor()), List.of(writer)),
                "同一写出器实例不得注入两个管理器");
        assertEquals("The response writer has already been initialized and can not be initialized again", thrown.getMessage());
        assertSame(first, writer.injectedExecutor(), "被拒的第二个管理器不得覆写首次注入的执行器");
    }

    @Test
    void constructorRejectsNullWriterElement() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new ResponseWriterManager(resourceManagerOf(Stubs.noopExecutor()), Arrays.asList((ResponseWriter) null)));
        assertEquals("The response writer must not be null", thrown.getMessage());
    }

    @Test
    void constructorRejectsNullWriterList() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new ResponseWriterManager(resourceManagerOf(Stubs.noopExecutor()), null));
        assertEquals("The custom writer list must not be null", thrown.getMessage());
    }

    @Test
    void constructorRejectsNullResourceManager() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new ResponseWriterManager(null, List.of()));
        assertEquals("The resource provider must not be null", thrown.getMessage());
    }

    @Test
    void initWriterHandsProviderToCustomWritersForBuiltinFetch() {
        MicroTaskExecutor executor = Stubs.noopExecutor();
        ResourceProvider provider = Stubs.diskPoolProvider(executor);
        MarkingWriter writer = new MarkingWriter();

        new ResponseWriterManager(provider, List.of(writer));

        assertSame(provider, writer.injectedProvider(), "构造期必须把资源提供者交给自定义写出器");
        assertSame(executor, writer.injectedProvider().getBuiltinResource(ResourceNames.DISK_IO_THREAD_POOL, MicroTaskExecutor.class),
                "写出器可从资源提供者按名取内置磁盘IO执行器");
    }

    /**
     * 构造测试用资源管理器：把给定执行器以内置空间登记为磁盘IO执行器资源，
     * 供写出器管理器按 {@link ResourceNames#DISK_IO_THREAD_POOL} 从其中获取
     */
    private static ResourceManager resourceManagerOf(MicroTaskExecutor executor) {
        Map<String, AutoCloseable> builtins = new LinkedHashMap<>();
        builtins.put(ResourceNames.DISK_IO_THREAD_POOL, executor);
        return new ResourceManager.Builder().build(builtins);
    }
}
