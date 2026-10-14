package top.turboweb.http.core.response;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.turboweb.http.exception.ResponseHandleException;
import top.turboweb.http.orchestrator.AsyncFileResponseWriter;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AsyncFileResponse} 的 AIO 通道生命周期与位置窗口。
 *
 * <p>构造会经 {@code WorkStealThreadUtils.getExecutorService()} 启动 JVM 级共享工作窃取池（spec §7.1）：
 * 用例不拥有该池，不 shutdown 亦不断言线程终止。本类每条用例都 close 掉自己打开的
 * {@link AsynchronousFileChannel}（try-with-resources 或 finally）。
 */
class AsyncFileResponseTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void defaultChunkSizeAndLength() throws IOException {
        AsyncFileResponse resp = new AsyncFileResponse(write("a.bin", "0123456789").toFile());
        try {
            assertEquals(8192, resp.chunkBuffer().capacity());
            assertEquals(10L, resp.getRemaining());
            assertEquals(0L, resp.getPosition());
            assertEquals("10", resp.headers().get(HttpHeaderNames.CONTENT_LENGTH));
            assertEquals(AsyncFileResponseWriter.class, resp.getWriterType());
            assertTrue(resp.getAsynchronousFileChannel().isOpen());
        } finally {
            resp.close();
        }
    }

    @Test
    void positionMovesRemainingWindow() throws IOException {
        try (AsyncFileResponse resp = new AsyncFileResponse(write("a.bin", "0123456789").toFile(), 4)) {
            resp.setPosition(6L);
            assertEquals(6L, resp.getPosition());
            assertEquals(4L, resp.getRemaining());
            assertEquals(4, resp.chunkBuffer().capacity());
        }
    }

    @Test
    void closeIsIdempotentAndClosesChannel() throws IOException {
        AsyncFileResponse resp = new AsyncFileResponse(write("a.bin", "0123456789").toFile());
        AsynchronousFileChannel channel = resp.getAsynchronousFileChannel();

        resp.close();
        assertFalse(channel.isOpen());
        assertDoesNotThrow(resp::close, "二次 close 必须幂等");
    }

    @Test
    void nonexistentFileThrowsBeforeOpeningChannel() {
        Path missing = tempDir.resolve("nope.bin");
        ResponseHandleException ex = assertThrows(ResponseHandleException.class,
                () -> new AsyncFileResponse(missing.toFile()));
        assertEquals("文件不存在," + missing.toFile().getAbsolutePath(), ex.getMessage());
    }
}
