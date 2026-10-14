package top.turboweb.http.orchestrator;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.http.exception.ResponseHandleException;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.core.response.FileStreamResponse;
import top.turboweb.http.testkit.Stubs;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FileStreamResponseWriter} 的写出路径行为锚（复刻旧文件流策略用例面）：
 * 分块 generate→背压 request→DefaultHttpContent→LAST。
 * 线程池经 injectThreadPool 注入同步替身，整条链在调用线程上跑完，返回凭据即终态；
 * 拒绝型替身钉"忙即取消"。通道为 EmbeddedChannel，不碰真实 socket。
 */
class FileStreamResponseWriterTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    /** 建写出器并注入给定线程池替身（池经接口注入，与旧构造注入等义）。 */
    private static FileStreamResponseWriter newWriter(ThreadPool pool) {
        FileStreamResponseWriter writer = new FileStreamResponseWriter();
        writer.injectThreadPool(pool);
        return writer;
    }

    private static Object[] drain(EmbeddedChannel channel) {
        List<Object> out = new ArrayList<>();
        Object m;
        while ((m = channel.readOutbound()) != null) {
            out.add(m);
        }
        return out.toArray();
    }

    private static void releaseAll(Object[] messages) {
        for (Object m : messages) {
            ReferenceCountUtil.release(m);
        }
    }

    private static String concatBody(Object[] messages) {
        StringBuilder sb = new StringBuilder();
        for (Object m : messages) {
            if (m instanceof DefaultHttpContent c) {
                ByteBuf buf = c.content();
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                sb.append(new String(bytes, StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    private static FileStreamResponse response(Path file, int chunkSize, long offset, long length) {
        return new FileStreamResponse(HttpResponseStatus.OK, file.toFile(),
                GlobalConfig.getResponseCharset(), chunkSize, offset, length);
    }

    @Test
    @DisplayName("多分块直通：header + 逐块内容 + LAST，promise 同步成功，通道被关闭")
    void multiChunkFileIsStreamedThenCompletesPromise() throws IOException {
        FileStreamResponse resp = response(write("a.txt", "hello"), 2, 0, 5);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ResponseWriter.WriteResult result = newWriter(Stubs.directExecutorThreadPool())
                    .write(resp, new InternalConnectSession(channel));
            assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
            assertTrue(result.channelFuture().isSuccess());
            Object[] out = drain(channel);
            assertSame(resp, out[0], "第一个出站必须是响应头本身");
            assertEquals("hello", concatBody(out), "三块 2+2+1 拼回原文");
            assertInstanceOf(LastHttpContent.class, out[out.length - 1]);
            assertFalse(resp.getFileChannel().isOpen(), "doFinally 必须关掉文件通道");
            releaseAll(out);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("空请求体范围（length=0）：header + LAST，零内容块")
    void emptyWindowWritesHeaderAndLastOnly() throws IOException {
        FileStreamResponse resp = response(write("e.txt", ""), 2, 0, 0);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ResponseWriter.WriteResult result = newWriter(Stubs.directExecutorThreadPool())
                    .write(resp, new InternalConnectSession(channel));
            assertTrue(result.channelFuture().isSuccess());
            Object[] out = drain(channel);
            assertEquals(2, out.length, "只有头与 LAST");
            assertInstanceOf(LastHttpContent.class, out[1]);
            releaseAll(out);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("HS-25 回归：offset>0 的窗口按物理偏移读取（'hello' 的 [2,5) 实发 'llo'）")
    void offsetRangeIsReadFromItsPhysicalOffset() throws IOException {
        // doReadChunk 按流逻辑偏移做绝对读，读取起点与窗口同坐标——
        // "hello" 的 [2,5) 窗口实发 "llo"，Content-Length=3 与实体相符。
        FileStreamResponse resp = response(write("o.txt", "hello"), 8, 2, 3);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ResponseWriter.WriteResult result = newWriter(Stubs.directExecutorThreadPool())
                    .write(resp, new InternalConnectSession(channel));
            assertTrue(result.channelFuture().isSuccess());
            Object[] out = drain(channel);
            assertEquals("llo", concatBody(out), "修复形状：窗口起点即物理偏移，不再从文件头错位读");
            releaseAll(out);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("订阅即被线程池拒绝：promise 失败于 ResponseHandleException，只发过头")
    void poolRejectAtSubscribeCancels() throws IOException {
        FileStreamResponse resp = response(write("r.txt", "hello"), 2, 0, 5);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ResponseWriter.WriteResult result = newWriter(Stubs.rejectingThreadPool())
                    .write(resp, new InternalConnectSession(channel));
            assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType(), "取消置败仍有凭据可监听");
            assertInstanceOf(ResponseHandleException.class, result.channelFuture().cause());
            Object[] out = drain(channel);
            assertEquals(1, out.length, "拒绝发生在首块之前，仅有头");
            assertFalse(resp.getFileChannel().isOpen());
            releaseAll(out);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("发出一块后线程池拒绝：该块已写出、无 LAST，promise 失败")
    void poolRejectAfterFirstChunkCancelsMidStream() throws IOException {
        FileStreamResponse resp = response(write("m.txt", "hello"), 2, 0, 5);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ResponseWriter.WriteResult result = newWriter(Stubs.firstRunRestRejectingThreadPool())
                    .write(resp, new InternalConnectSession(channel));
            assertInstanceOf(ResponseHandleException.class, result.channelFuture().cause());
            Object[] out = drain(channel);
            assertEquals(2, out.length, "头 + 一个内容块，然后取消");
            assertEquals("he", concatBody(out));
            releaseAll(out);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("非 FileStreamResponse 走模板兜底：记日志关连接，不抛穿")
    void wrongTypeIsCaughtByTemplateAndClosesConnection() {
        DefaultFullHttpResponse plain =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ResponseWriter.WriteResult result = newWriter(Stubs.directExecutorThreadPool())
                    .write(plain, new InternalConnectSession(channel));
            assertEquals(ResponseWriter.WriteType.NOT_LISTENABLE, result.writeType(),
                    "写出异常无凭据，结果不可监听");
            assertNull(result.channelFuture(), "不可监听结果的凭据恒为空");
            assertNull(channel.readOutbound(), "兜底不再写出错误页");
            channel.runPendingTasks();
            assertFalse(channel.isActive(), "写出异常必须关闭连接");
            plain.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
