package top.turboweb.http.orchestrator.writer;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.turboweb.http.connection.InternalHttpConnection;
import top.turboweb.http.response.ZeroCopyResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ZeroCopyResponseWriter} 的写出路径行为锚：三段式（头、FileRegion、结束帧）
 * 同步写出与虚拟线程契约。EmbeddedChannel 出站队列原样收集消息、不做真实传输，
 * 序列形状可直接断言。
 */
class ZeroCopyResponseWriterTest {

    @TempDir
    Path tempDir;

    /** 在虚拟线程里跑 body，返回其结果；超时未返回即红。 */
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

    private ZeroCopyResponse newResponse() throws IOException {
        Path file = tempDir.resolve("z.bin");
        Files.writeString(file, "hello", StandardCharsets.UTF_8);
        return new ZeroCopyResponse(file.toFile());
    }

    @Test
    @DisplayName("正常零拷贝：头 + FileRegion + LAST 三段依次写出，promise 成功")
    void happyPathWritesHeaderRegionThenLast() throws Exception {
        ZeroCopyResponse resp = newResponse();
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ResponseWriter.WriteResult result = writeOnVirtualThread(
                    new ZeroCopyResponseWriter(), resp, new InternalHttpConnection(channel));
            assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
            assertTrue(result.channelFuture().isSuccess());
            List<Object> out = drain(channel);
            assertEquals(3, out.size(), "头、文件区、结束帧三段");
            assertSame(resp, out.get(0));
            assertSame(resp.getFileRegion(), out.get(1), "第二段必须是响应携带的 FileRegion 本体");
            assertSame(LastHttpContent.EMPTY_LAST_CONTENT, out.get(2));
            ReferenceCountUtil.release(resp.getFileRegion()); // 释放区域自带的文件通道
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("虚拟线程里喂错类型：IAE 走模板兜底，关连接返回不可监听")
    void wrongTypeOnVirtualThreadClosesConnection() throws Exception {
        DefaultFullHttpResponse plain =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ResponseWriter.WriteResult result = writeOnVirtualThread(
                    new ZeroCopyResponseWriter(), plain, new InternalHttpConnection(channel));
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
    void platformThreadIsRejectedAndClosesConnection() throws Exception {
        ZeroCopyResponse resp = newResponse();
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            // 就在当前（JUnit 平台）线程直调，不自起虚拟线程
            ResponseWriter.WriteResult result = new ZeroCopyResponseWriter()
                    .write(resp, new InternalHttpConnection(channel));
            assertEquals(ResponseWriter.WriteType.NOT_LISTENABLE, result.writeType(),
                    "写出异常无凭据，结果不可监听");
            assertNull(result.channelFuture(), "不可监听结果的凭据恒为空");
            assertEquals(0, drain(channel).size(), "兜底不再写出错误页（断言若被删除会先写出响应头）");
            channel.runPendingTasks();
            assertFalse(channel.isActive(), "写出异常必须关闭连接");
            ReferenceCountUtil.release(resp.getFileRegion()); // 未写出，测试侧回收
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
