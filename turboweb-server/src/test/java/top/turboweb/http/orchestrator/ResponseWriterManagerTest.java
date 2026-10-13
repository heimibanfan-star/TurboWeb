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
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.core.response.ResponseCall;
import top.turboweb.http.testkit.Stubs;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ResponseWriterManager} 的行为锚：三级选择次序
 * （响应经 ResponseCall 声明写出器类型——内置表优先、自定义表其次、默认写出器兜底）、
 * 自定义写出器登记规则（系统内置类型拒登、同类重复拒登、登记即注入线程池）,
 * 以及装配期线程池非空约束。
 * 选择结果经各写出器互异的写出副作用甄别：忽略返回 null 且零出站、
 * 默认补齐 Content-Length、自定义只置标记、Reactor 类型在平台线程被虚拟线程契约兜成 500。
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

    /** 自定义写出器夹具：不写出任何内容，仅置写出标记，用于甄别自定义表命中。 */
    private static final class MarkingWriter extends AbstractResponseWriter {

        private final AtomicBoolean written = new AtomicBoolean(false);

        @Override
        protected ChannelFuture doWrite(HttpResponse response, InternalConnectSession session) {
            written.set(true);
            return session.getChannel().newPromise().setSuccess();
        }

        ThreadPool injectedPool() {
            return threadPool;
        }

        boolean wasWritten() {
            return written.get();
        }
    }

    @Test
    void declaredInternalWriterTypeHitsInternalTable() {
        EmbeddedChannel channel = new EmbeddedChannel();
        CallResponse response = new CallResponse(IgnoredResponseWriter.class);

        ChannelFuture future = new ResponseWriterManager(Stubs.threadPool())
                .write(response, new InternalConnectSession(channel));

        assertNull(future, "内置忽略写出器命中：无写出凭据");
        assertNull(channel.readOutbound(), "默认路径会写出响应帧，此处必须为空");
        channel.finishAndReleaseAll();
    }

    @Test
    void declaredWriterTypeWithoutRegistrationFallsToDefault() {
        EmbeddedChannel channel = new EmbeddedChannel();
        CallResponse response = new CallResponse(MarkingWriter.class);

        ChannelFuture future = new ResponseWriterManager(Stubs.threadPool())
                .write(response, new InternalConnectSession(channel));

        assertTrue(future.isSuccess());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH),
                "声明类型未登记即走默认写出器（补齐 Content-Length）");
        assertSame(response, channel.readOutbound());
        channel.finishAndReleaseAll();
    }

    @Test
    void registeredCustomWriterIsSelectedByDeclaration() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ResponseWriterManager manager = new ResponseWriterManager(Stubs.threadPool());
        MarkingWriter writer = new MarkingWriter();
        manager.addResponseWriter(writer);

        ChannelFuture future = manager.write(
                new CallResponse(MarkingWriter.class), new InternalConnectSession(channel));

        assertTrue(future.isSuccess());
        assertTrue(writer.wasWritten(), "声明类型命中自定义表即由登记的写出器写出");
        assertNull(channel.readOutbound(), "自定义写出器不落任何出站帧，默认路径则会写出响应");
        channel.finishAndReleaseAll();
    }

    @Test
    void responseWithoutCallDeclarationFallsToDefault() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultFullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.content().writeCharSequence("abc", StandardCharsets.UTF_8);

        ChannelFuture future = new ResponseWriterManager(Stubs.threadPool())
                .write(response, new InternalConnectSession(channel));

        assertTrue(future.isSuccess());
        assertEquals("3", response.headers().get(HttpHeaderNames.CONTENT_LENGTH),
                "未实现 ResponseCall 的响应由默认写出器兜底");
        assertSame(response, channel.readOutbound());
        channel.finishAndReleaseAll();
    }

    @Test
    void declaredReactorWriterOnPlatformThreadIsRejectedAs500() {
        // 默认路径会把该响应按 200 原样写出；出站出现 500 错误页
        // 即证明声明类型命中内置表的 Reactor 写出器（其虚拟线程契约被模板兜底）
        EmbeddedChannel channel = new EmbeddedChannel();
        CallResponse response = new CallResponse(ReactorResponseWriter.class);

        ChannelFuture future = new ResponseWriterManager(Stubs.threadPool(), true)
                .write(response, new InternalConnectSession(channel));

        assertTrue(future.isSuccess(), "模板兜底的 500 页写出应成功");
        DefaultFullHttpResponse err = assertInstanceOf(DefaultFullHttpResponse.class, channel.readOutbound());
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, err.status());
        err.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void registeringBuiltinWriterIsRejected() {
        ResponseWriterManager manager = new ResponseWriterManager(Stubs.threadPool());
        List<ResponseWriter> builtins = List.of(
                new SseResponseWriter(),
                new FileStreamResponseWriter(),
                new DefaultResponseWriter(),
                new AsyncFileResponseWriter(),
                new ZeroCopyResponseWriter(),
                new ReactorResponseWriter(false),
                new IgnoredResponseWriter());
        for (ResponseWriter builtin : builtins) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> manager.addResponseWriter(builtin),
                    builtin.getClass().getSimpleName() + " 为系统内置写出器，不得登记");
            assertEquals("不能注册系统内置的响应写出器:" + builtin.getClass().getName(), thrown.getMessage());
        }
    }

    @Test
    void duplicateRegistrationIsRejected() {
        ResponseWriterManager manager = new ResponseWriterManager(Stubs.threadPool());
        manager.addResponseWriter(new MarkingWriter());

        IllegalArgumentException again = assertThrows(IllegalArgumentException.class,
                () -> manager.addResponseWriter(new MarkingWriter()));
        assertEquals("响应写出器不能重复注册:" + MarkingWriter.class.getName(), again.getMessage());

        // 重复判定按写出器类进行：同类不同实例同样拒登
        assertThrows(IllegalArgumentException.class, () -> manager.addResponseWriter(new MarkingWriter()));
    }

    @Test
    void registeredWriterReceivesThreadPoolInjection() {
        ThreadPool pool = Stubs.directExecutorThreadPool();
        ResponseWriterManager manager = new ResponseWriterManager(pool);
        MarkingWriter writer = new MarkingWriter();

        manager.addResponseWriter(writer);

        assertSame(pool, writer.injectedPool(), "登记时必须把磁盘IO线程池注入自定义写出器");
    }

    @Test
    void addResponseWriterRejectsNull() {
        ResponseWriterManager manager = new ResponseWriterManager(Stubs.threadPool());
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> manager.addResponseWriter(null));
        assertEquals("响应写出器不能为null", thrown.getMessage());
    }

    @Test
    void constructorRejectsNullThreadPool() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new ResponseWriterManager(null));
        assertEquals("磁盘IO线程池不能为null", thrown.getMessage());
        assertThrows(NullPointerException.class, () -> new ResponseWriterManager(null, true));
    }
}
