package top.turboweb.http.core.response;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboFileException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link FileStreamResponse} 的构造参数落位与 {@link FileChannel} 释放契约。
 * 每条用例都必须关掉构造期打开的 FileChannel（产品不提供 close，调用方负责）。
 */
class FileStreamResponseTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void defaultConstructorUsesGlobalCharsetAndFullRange() throws IOException {
        FileStreamResponse resp = new FileStreamResponse(write("a.txt", "hello").toFile());
        try {
            assertEquals(0L, resp.getOffset());
            assertEquals(5L, resp.getLength());
            assertEquals(5L, resp.getEnd());
            assertEquals(2097152L, resp.getChunkSize());
            assertEquals(GlobalConfig.getResponseCharset(), resp.getFilenameCharset());
            assertEquals("5", resp.headers().get(HttpHeaderNames.CONTENT_LENGTH));
            assertEquals(InternalCallResponse.InternalCallType.FILE_STREAM, resp.getType());
        } finally {
            resp.getFileChannel().close();
        }
    }

    @Test
    void customChunkSizeIsHonored() throws IOException {
        FileStreamResponse resp = new FileStreamResponse(write("a.txt", "hello").toFile(), 1024);
        try {
            assertEquals(1024L, resp.getChunkSize());
        } finally {
            resp.getFileChannel().close();
        }
    }

    @Test
    void customFilenameCharsetIsHonored() throws IOException {
        FileStreamResponse resp =
                new FileStreamResponse(write("a.txt", "hello").toFile(), StandardCharsets.ISO_8859_1);
        try {
            assertEquals(StandardCharsets.ISO_8859_1, resp.getFilenameCharset());
        } finally {
            resp.getFileChannel().close();
        }
    }

    @Test
    void offsetAndLengthAreStoredVerbatim() throws IOException {
        FileStreamResponse resp = new FileStreamResponse(write("a.txt", "hello").toFile(), 2L, 3L);
        try {
            assertEquals(2L, resp.getOffset());
            assertEquals(3L, resp.getLength());
            assertEquals(5L, resp.getEnd());
            // 钉住现状：CONTENT_LENGTH 取 length（3），不是 end（5）
            assertEquals("3", resp.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        } finally {
            resp.getFileChannel().close();
        }
    }

    @Test
    void customStatusAndRangeAreHonored() throws IOException {
        FileStreamResponse resp = new FileStreamResponse(
                HttpResponseStatus.PARTIAL_CONTENT, write("a.txt", "hello").toFile(),
                StandardCharsets.UTF_8, 128, 1L, 2L);
        try {
            assertEquals(HttpResponseStatus.PARTIAL_CONTENT, resp.status());
            assertEquals(128L, resp.getChunkSize());
            assertEquals(3L, resp.getEnd());
        } finally {
            resp.getFileChannel().close();
        }
    }

    @Test
    void nonexistentFileThrowsWithoutOpeningChannel() {
        Path missing = tempDir.resolve("nope.txt");
        TurboFileException ex = assertThrows(TurboFileException.class,
                () -> new FileStreamResponse(missing.toFile()));
        assertEquals("文件不存在," + missing.toFile().getAbsolutePath(), ex.getMessage());
    }

    @Test
    void directoryThrows() {
        TurboFileException ex = assertThrows(TurboFileException.class,
                () -> new FileStreamResponse(tempDir.toFile()));
        assertEquals("文件是文件夹," + tempDir.toFile().getAbsolutePath(), ex.getMessage());
    }

    @Test
    void closedChannelIsIdempotentAndRejectsReads() throws IOException {
        FileStreamResponse resp = new FileStreamResponse(write("a.txt", "hello").toFile());
        FileChannel channel = resp.getFileChannel();

        channel.close();
        assertFalse(channel.isOpen());
        assertDoesNotThrow(channel::close, "FileChannel.close 必须幂等，二次关闭不得抛");

        assertThrows(ClosedChannelException.class, () -> channel.read(ByteBuffer.allocate(1), 0L),
                "释放后再操作必须快速失败，不能静默读到旧数据");
    }
}
