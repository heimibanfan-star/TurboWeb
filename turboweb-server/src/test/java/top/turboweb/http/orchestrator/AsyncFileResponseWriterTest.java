package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
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
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.core.response.AsyncFileResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AsyncFileResponseWriter} 的行为锚：返回头写出凭据、AIO 环在后台线程完成后
 * 落结束帧并关文件通道、错型经模板兜底 500。
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
    @DisplayName("正常 AIO 流：返回头写出凭据，后台完成后落 LAST 并关文件通道")
    void headerFutureReturnedAndStreamCompletesAsynchronously() throws Exception {
        AsyncFileResponse resp = newResponse(2);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ChannelFuture f = new AsyncFileResponseWriter()
                    .write(resp, new InternalConnectSession(channel));
            assertSame(resp, channel.readOutbound(), "第一个出站必须是响应头本身");
            assertTrue(f.isSuccess(), "返回值即响应头写出的凭据");
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
    @DisplayName("非 AsyncFileResponse 走模板兜底：500 错误页，不抛穿")
    void wrongTypeIsCaughtByTemplateAndWrittenAs500() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultFullHttpResponse plain =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        try {
            ChannelFuture f = new AsyncFileResponseWriter()
                    .write(plain, new InternalConnectSession(channel));
            assertTrue(f.isSuccess(), "模板方法把类型异常兜成错误响应写回");
            DefaultFullHttpResponse err = assertInstanceOf(DefaultFullHttpResponse.class, channel.readOutbound());
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, err.status());
            plain.release();
            err.release();
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
            ChannelFuture f = new AsyncFileResponseWriter()
                    .write(resp, new InternalConnectSession(channel));
            assertTrue(f.isDone());
            assertFalse(f.isSuccess(), "头写失败必须原样反映在返回凭据上");
            channel.runPendingTasks();
            assertNull(channel.readOutbound(), "写头失败不得有出站消息");
        } finally {
            resp.close();
            channel.finishAndReleaseAll();
        }
    }
}
