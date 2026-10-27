package top.turboweb.http.orchestrator;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.turboweb.http.core.connect.InternalHttpConnection;
import top.turboweb.http.core.response.AsyncFileResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AsyncFileResponseWriter} 的行为锚：返回可监听结果携头写出凭据、AIO 环在后台线程完成后
 * 落结束帧并关文件通道、错型经模板兜底记日志并关闭连接。
 * AIO 环复用同一直分隔缓冲（写出即视为消费），EmbeddedChannel 收集语义下逐块内容不可靠，
 * 故只钉终态形状（出现内容帧 + LAST + 通道关闭），不校验字节序列。
 */
class AsyncFileResponseWriterTest {

    @TempDir
    Path tempDir;

    private AsyncFileResponse newResponse(int chunkSize) throws IOException {
        Path file = tempDir.resolve("a.bin");
        Files.writeString(file, "hello", StandardCharsets.UTF_8);
        return new AsyncFileResponse(file.toFile(), chunkSize);
    }

    private static List<Object> drain(EmbeddedChannel channel) {
        List<Object> out = new ArrayList<>();
        Object m;
        while ((m = channel.readOutbound()) != null) {
            out.add(m);
        }
        return out;
    }

    /** 跨轮次累计地轮询等待 AIO 环写出结束帧（后台线程驱动，有界等待防挂死）。 */
    private static List<Object> awaitLastContent(EmbeddedChannel channel) throws InterruptedException {
        List<Object> collected = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            channel.runPendingTasks();
            collected.addAll(drain(channel));
            if (!collected.isEmpty() && collected.get(collected.size() - 1) instanceof LastHttpContent) {
                return collected;
            }
            Thread.sleep(10);
        }
        for (Object m : collected) {
            ReferenceCountUtil.release(m);
        }
        fail("AIO 传输未在限时内写出结束帧");
        return List.of();
    }

    @Test
    @DisplayName("正常 AIO 流：返回可监听结果携头写出凭据，后台完成后落 LAST 并关文件通道")
    void headerFutureReturnedAndStreamCompletesAsynchronously() throws Exception {
        AsyncFileResponse resp = newResponse(2);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ResponseWriter.WriteResult result = new AsyncFileResponseWriter()
                    .write(resp, new InternalHttpConnection(channel));
            assertSame(resp, channel.readOutbound(), "第一个出站必须是响应头本身");
            assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
            assertTrue(result.channelFuture().isSuccess(), "凭据即响应头写出的结果");
            List<Object> out = awaitLastContent(channel);
            assertInstanceOf(LastHttpContent.class, out.get(out.size() - 1));
            assertTrue(out.stream().anyMatch(m -> m instanceof DefaultHttpContent), "应出现内容分块");
            for (Object m : out) {
                ReferenceCountUtil.release(m);
            }
            long deadline = System.currentTimeMillis() + 3000;
            while (resp.getAsynchronousFileChannel().isOpen() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10); // 关闭发生在 AIO 回调线程
            }
            assertFalse(resp.getAsynchronousFileChannel().isOpen(), "传输完成必须关闭文件通道");
        } finally {
            resp.close(); // 幂等，兜底释放
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("非 AsyncFileResponse 走模板兜底：记日志关连接，不抛穿")
    void wrongTypeIsCaughtByTemplateAndClosesConnection() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultFullHttpResponse plain =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        try {
            ResponseWriter.WriteResult result = new AsyncFileResponseWriter()
                    .write(plain, new InternalHttpConnection(channel));
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

    @Test
    @DisplayName("通道已关：头写失败直接体现在返回凭据上，不触发内容写出")
    void closedChannelFailsHeaderFutureWithoutBody() throws Exception {
        AsyncFileResponse resp = newResponse(2);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.close().sync();
        try {
            ResponseWriter.WriteResult result = new AsyncFileResponseWriter()
                    .write(resp, new InternalHttpConnection(channel));
            assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType(), "写失败仍有凭据可监听");
            assertTrue(result.channelFuture().isDone());
            assertFalse(result.channelFuture().isSuccess(), "头写失败必须原样反映在返回凭据上");
            channel.runPendingTasks();
            assertNull(channel.readOutbound(), "写头失败不得有出站消息");
        } finally {
            resp.close();
            channel.finishAndReleaseAll();
        }
    }
}
