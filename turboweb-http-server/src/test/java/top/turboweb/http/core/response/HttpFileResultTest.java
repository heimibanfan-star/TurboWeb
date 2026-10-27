package top.turboweb.http.core.response;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.turboweb.http.orchestrator.FileStreamResponseWriter;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpFileResult} 的文件元信息、内存/流式两条响应分支与可切片阈值边界（spec §5：response）。
 *
 * <p>阈值分支不构造 32MB 大文件、也不真读大文件：{@code maxLimitSize} 无 setter，
 * 只有单向的 {@link HttpFileResult#closeLimit()}，因此用反射把阈值压到个位数，
 * 再以 {@link TempDir} 下的十几字节小文件跨过/恰好贴住边界。
 */
class HttpFileResultTest {

    private static final long DEFAULT_MAX_LIMIT = 33554432L;
    private static final byte[] BODY = "0123456789".getBytes(StandardCharsets.UTF_8); // 10 字节

    @TempDir
    Path tempDir;

    /** 反射写 `maxLimitSize`：产品无 setter，closeLimit() 只会置 -1。 */
    private static void setMaxLimit(HttpFileResult result, long limit) throws Exception {
        Field field = HttpFileResult.class.getDeclaredField("maxLimitSize");
        field.setAccessible(true);
        field.setLong(result, limit);
    }

    private static long maxLimitOf(HttpFileResult result) throws Exception {
        Field field = HttpFileResult.class.getDeclaredField("maxLimitSize");
        field.setAccessible(true);
        return field.getLong(result);
    }

    private File file(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path.toFile();
    }

    private static String dispositionFileName(HttpResponse response) {
        String value = response.headers().get(HttpHeaderNames.CONTENT_DISPOSITION);
        return value.substring(value.indexOf('"') + 1, value.lastIndexOf('"'));
    }

    @Test
    void byteSourceKeepsMetadataAndBody() {
        HttpFileResult result = HttpFileResult.bytes(BODY, "a b.txt", "text/plain", false);
        FullHttpResponse response = (FullHttpResponse) result.createResponse(StandardCharsets.UTF_8);
        try {
            assertEquals(200, response.status().code());
            assertEquals("text/plain", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            assertEquals("attachment;filename=\"a%20b.txt\"",
                    response.headers().get(HttpHeaderNames.CONTENT_DISPOSITION),
                    "空格必须编码成 %20，不得留下 URLEncoder 的 '+'");
            // brief 的 HttpHeaders.getLong(...) 在 Netty 4.x 不存在（javac 找不到符号），编译修复为 getInt 钉同一头值
            assertEquals(10, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
            assertEquals(10, response.content().readableBytes());
            assertEquals("0123456789", response.content().toString(StandardCharsets.UTF_8));
        } finally {
            response.release();
        }
    }

    @Test
    void nullOrEmptyFilenameFallsBackToUuid() {
        for (String name : new String[]{null, ""}) {
            HttpFileResult result = HttpFileResult.bytes(BODY, name);
            FullHttpResponse response = (FullHttpResponse) result.createResponse(StandardCharsets.UTF_8);
            try {
                String encoded = dispositionFileName(response);
                assertEquals(encoded, UUID.fromString(encoded).toString(), "缺名时文件名应是随机 UUID");
            } finally {
                response.release();
            }
        }
    }

    @Test
    void diskFileUsesProvidedContentTypeAndLength() throws Exception {
        File source = file("data.bin", "hello");
        HttpFileResult result = HttpFileResult.file(source, "application/x-demo", false);
        FullHttpResponse response = (FullHttpResponse) result.createResponse(StandardCharsets.UTF_8);
        try {
            assertEquals("application/x-demo", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            assertEquals("attachment;filename=\"data.bin\"",
                    response.headers().get(HttpHeaderNames.CONTENT_DISPOSITION));
            assertEquals(5, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
            assertEquals("hello", response.content().toString(StandardCharsets.UTF_8));
        } finally {
            response.release();
        }
    }

    @Test
    void missingContentTypeFallsBackToOctetStream() throws Exception {
        File source = file("blob", "abc");
        for (String contentType : new String[]{null, ""}) {
            HttpFileResult result = HttpFileResult.file(source, contentType, false);
            FullHttpResponse response = (FullHttpResponse) result.createResponse(StandardCharsets.UTF_8);
            try {
                assertEquals("application/octet-stream",
                        response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            } finally {
                response.release();
            }
        }
    }

    @Test
    void openFileFlagSelectsInlineDisposition() throws Exception {
        File source = file("pic.png", "PNG");
        FullHttpResponse inline =
                (FullHttpResponse) HttpFileResult.file(source, "image/png", true).createResponse(StandardCharsets.UTF_8);
        FullHttpResponse attachment =
                (FullHttpResponse) HttpFileResult.file(source, "image/png", false).createResponse(StandardCharsets.UTF_8);
        try {
            assertEquals("inline;filename=\"pic.png\"",
                    inline.headers().get(HttpHeaderNames.CONTENT_DISPOSITION));
            assertEquals("attachment;filename=\"pic.png\"",
                    attachment.headers().get(HttpHeaderNames.CONTENT_DISPOSITION));
        } finally {
            inline.release();
            attachment.release();
        }
    }

    @Test
    void imageAndVideoFactoriesCarryTheirMimeTypesInline() {
        assertFactoryMetadata(HttpFileResult.png(BODY, "a.png"), "image/png");
        assertFactoryMetadata(HttpFileResult.png(BODY), "image/png");
        assertFactoryMetadata(HttpFileResult.jpeg(BODY, "a.jpg"), "image/jpeg");
        assertFactoryMetadata(HttpFileResult.jpeg(BODY), "image/jpeg");
        assertFactoryMetadata(HttpFileResult.gif(BODY, "a.gif"), "image/gif");
        assertFactoryMetadata(HttpFileResult.gif(BODY), "image/gif");
        assertFactoryMetadata(HttpFileResult.mp4(BODY, "a.mp4"), "video/mp4");
        assertFactoryMetadata(HttpFileResult.mp4(BODY), "video/mp4");
    }

    private static void assertFactoryMetadata(HttpFileResult result, String contentType) {
        FullHttpResponse response = (FullHttpResponse) result.createResponse(StandardCharsets.UTF_8);
        try {
            assertEquals(contentType, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            assertTrue(response.headers().get(HttpHeaderNames.CONTENT_DISPOSITION).startsWith("inline;"),
                    "图片/视频工厂必须 inline 打开");
            assertEquals(BODY.length, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
        } finally {
            response.release();
        }
    }

    @Test
    void overLimitFileBecomesChunkedFileStream() throws Exception {
        File source = file("stream.bin", "0123456789"); // 10 字节
        HttpFileResult result = HttpFileResult.file(source, "application/x-stream", false);
        setMaxLimit(result, 9); // 10 > 9 → 流式分支
        HttpResponse response = result.createResponse(StandardCharsets.UTF_8);
        try {
            assertInstanceOf(FileStreamResponse.class, response, "超限文件必须走 FileStreamResponse");
            assertEquals(FileStreamResponseWriter.class,
                    ((ResponseCall) response).getWriterType(), "超限文件结果声明 FileStreamResponseWriter 负责写出");
            assertEquals("application/x-stream", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            assertEquals("attachment;filename=\"stream.bin\"",
                    response.headers().get(HttpHeaderNames.CONTENT_DISPOSITION));
            assertEquals(10, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
        } finally {
            FileStreamResponse.class.cast(response).getFileChannel().close();
        }
    }

    @Test
    void fileExactlyAtLimitStaysInMemory() throws Exception {
        File source = file("edge.bin", "0123456789"); // 10 字节
        HttpFileResult result = HttpFileResult.file(source, "application/octet-stream", false);
        setMaxLimit(result, 10); // 长度 == 限制：判据是 >，所以不切片
        FullHttpResponse response = (FullHttpResponse) result.createResponse(StandardCharsets.UTF_8);
        try {
            assertEquals(10, response.content().readableBytes(), "恰好等于限制不得切片");
        } finally {
            response.release();
        }
    }

    @Test
    void closeLimitForcesInMemoryMode() throws Exception {
        File source = file("nolimit.bin", "0123456789"); // 10 字节
        HttpFileResult result = HttpFileResult.file(source, null, false);
        setMaxLimit(result, 1); // 本会超限
        result.closeLimit();    // 关闭限制 → 永不切片
        FullHttpResponse response = (FullHttpResponse) result.createResponse(StandardCharsets.UTF_8);
        try {
            assertEquals(10, response.content().readableBytes());
        } finally {
            response.release();
        }
    }

    @Test
    void defaultLimitIsThirtyTwoMiB() {
        HttpFileResult result = HttpFileResult.bytes(BODY);
        assertDoesNotThrow(() -> assertEquals(DEFAULT_MAX_LIMIT, maxLimitOf(result)),
                "可切片阈值默认 32MB");
    }

    @Test
    void nonExistentFileIsRejected() {
        File ghost = tempDir.resolve("nope.txt").toFile();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> HttpFileResult.file(ghost));
        assertEquals("file not exists", ex.getMessage());
    }

    @Test
    void directoryIsRejected() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> HttpFileResult.file(tempDir.toFile()));
        assertEquals("file is directory", ex.getMessage());
    }

    @Test
    void nullArgumentsAreRejected() {
        NullPointerException fileEx = assertThrows(NullPointerException.class,
                () -> new HttpFileResult((File) null, null, false));
        assertEquals("file can not be null", fileEx.getMessage());

        NullPointerException bufferEx = assertThrows(NullPointerException.class,
                () -> new HttpFileResult((ByteBuffer) null, "x", null, false));
        assertEquals("buffer can not be null", bufferEx.getMessage());
    }
}
