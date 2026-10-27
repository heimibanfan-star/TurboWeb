package top.turboweb.http.orchestrator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.turboweb.http.core.connect.InternalHttpConnection;
import top.turboweb.http.core.response.ReactorResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ReactorResponseWriter} 的写出路径行为锚（复刻旧 Reactor 策略用例面）。
 * doWrite 首行钉死虚拟线程契约，全部经 write 在自起虚拟线程里执行、
 * join(5000) 等返回值——流由 Flux.just/error 驱动、通道是 EmbeddedChannel，链路同步可断言。
 * 正常与置败路径可监听，promise 的成败终态经凭据反映；
 * 类型错配与契约拒绝异常走模板兜底：记录日志、关闭连接、返回不可监听结果。
 */
class ReactorResponseWriterTest {

    /** 在虚拟线程里跑 body，返回其结果；超时未返回即红（不拖死 worker：守护语义由 join 上界保证）。 */
    private static ResponseWriter.WriteResult writeOnVirtualThread(ResponseWriter writer,
                                                                   HttpResponse response,
                                                                   InternalHttpConnection session) throws InterruptedException {
        AtomicReference<ResponseWriter.WriteResult> box = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread t = Thread.ofVirtual().start(() -> {
            try {
                box.set(writer.write(response, session));
            } catch (Throwable te) {
                err.set(te);
            }
        });
        t.join(5000);
        assertFalse(t.isAlive(), "write 必须及时返回（链路同步）");
        assertNull(err.get(), "write 模板方法不得让异常逃逸");
        return box.get();
    }

    private static List<Object> drain(EmbeddedChannel channel) {
        List<Object> out = new ArrayList<>();
        Object m;
        while ((m = channel.readOutbound()) != null) {
            out.add(m);
        }
        return out;
    }

    private static ByteBuf buf(String s) {
        return Unpooled.copiedBuffer(s, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("正常流：chunked 头 + 逐块内容 + LAST，promise 成功")
    void happyPathWritesHeaderChunksAndLast() throws InterruptedException {
        ReactorResponse resp = new ReactorResponse(Flux.just(buf("ab"), buf("cd")));
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection session = new InternalHttpConnection(channel);
        try {
            ResponseWriter.WriteResult result = writeOnVirtualThread(new ReactorResponseWriter(), resp, session);
            assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
            assertTrue(result.channelFuture().isSuccess());
            assertEquals("chunked", resp.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
            assertEquals("keep-alive", resp.headers().get(HttpHeaderNames.CONNECTION));
            List<Object> out = drain(channel);
            assertSame(resp, out.get(0));
            assertInstanceOf(DefaultHttpContent.class, out.get(1));
            assertInstanceOf(DefaultHttpContent.class, out.get(2));
            assertInstanceOf(LastHttpContent.class, out.get(3));
            assertEquals(4, out.size());
            for (Object m : out) {
                ReferenceCountUtil.release(m); // 含 Unpooled 堆缓冲内容块与头本身
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("流异常：promise 失败于原异常，close 任务经 runPendingTasks 关掉通道")
    void fluxErrorFailsPromiseAndClosesSession() throws InterruptedException {
        IllegalStateException boom = new IllegalStateException("boom");
        ReactorResponse resp = new ReactorResponse(Flux.error(boom));
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection session = new InternalHttpConnection(channel);
        try {
            ResponseWriter.WriteResult result = writeOnVirtualThread(new ReactorResponseWriter(), resp, session);
            assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType(),
                    "置败的 promise 仍是可监听凭据");
            assertSame(boom, result.channelFuture().cause());
            channel.runPendingTasks(); // session.close() 排入 eventLoop
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("响应头写失败（通道已关）：promise 置败，不抛穿 write")
    void headerWriteFailureSetsPromiseFailure() throws InterruptedException {
        ByteBuf ab = buf("ab"); // 头写失败则流永不订阅，该缓冲只能测试侧回收
        ReactorResponse resp = new ReactorResponse(Flux.just(ab));
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection session = new InternalHttpConnection(channel);
        channel.close().sync();
        try {
            ResponseWriter.WriteResult result = writeOnVirtualThread(new ReactorResponseWriter(), resp, session);
            ChannelFuture f = result.channelFuture();
            assertFalse(f.isSuccess(), "写头失败必须走置败分支");
            // pending promise 同样 isSuccess()==false——必须钉终态与真实 cause，
            // 否则删掉置败本用例仍绿。
            assertTrue(f.isDone(), "promise 必须经置败到达终态（pending 也会让 isSuccess()==false 通过）");
            assertNotNull(f.cause(), "置败必须携带写头失败的真实 cause");
            channel.runPendingTasks();
        } finally {
            ReferenceCountUtil.release(ab);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("虚拟线程里喂错类型：IAE 走模板兜底，关连接返回不可监听")
    void wrongTypeOnVirtualThreadClosesConnection() throws InterruptedException {
        DefaultFullHttpResponse plain =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ResponseWriter.WriteResult result = writeOnVirtualThread(
                    new ReactorResponseWriter(), plain, new InternalHttpConnection(channel));
            assertEquals(ResponseWriter.WriteType.NOT_LISTENABLE, result.writeType(),
                    "写出异常无凭据，结果不可监听");
            assertNull(result.channelFuture(), "不可监听结果的凭据恒为空");
            assertEquals(0, drain(channel).size(), "兜底不再写出错误页");
            channel.runPendingTasks();
            assertFalse(channel.isActive(), "写出异常必须关闭连接");
            plain.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("平台线程直接调用：虚拟线程断言走模板兜底，关连接返回不可监听")
    void platformThreadIsRejectedAndClosesConnection() {
        ByteBuf z = buf("z"); // 断言先于订阅抛出，该缓冲只能测试侧回收
        ReactorResponse resp = new ReactorResponse(Flux.just(z));
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            // 就在当前（JUnit 平台）线程直调 write，不自起虚拟线程
            ResponseWriter.WriteResult result = new ReactorResponseWriter()
                    .write(resp, new InternalHttpConnection(channel));
            assertEquals(ResponseWriter.WriteType.NOT_LISTENABLE, result.writeType(),
                    "写出异常无凭据，结果不可监听");
            assertNull(result.channelFuture(), "不可监听结果的凭据恒为空");
            assertEquals(0, drain(channel).size(), "兜底不再写出错误页（断言若被删除会先写出响应头）");
            channel.runPendingTasks();
            assertFalse(channel.isActive(), "写出异常必须关闭连接");
        } finally {
            ReferenceCountUtil.release(z);
            channel.finishAndReleaseAll();
        }
    }
}
