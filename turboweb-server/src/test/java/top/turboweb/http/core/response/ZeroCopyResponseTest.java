package top.turboweb.http.core.response;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.turboweb.http.exception.ResponseHandleException;
import top.turboweb.http.orchestrator.ZeroCopyResponseWriter;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ZeroCopyResponse} 的切片参数落位与 header 计算。
 *
 * <p>真零拷贝写出（{@code FileRegion.transferTo}）需真实可写通道 → 移交阶段 3/4；
 * 本类只钉构造期可观测的纯逻辑：region 的 position/count、Content-Length 头、文件名、通道归属。
 * 资源释放：File 构造的 region 未 transferTo 前不打开通道，{@code release()} 不产生句柄；
 * FileChannel 构造的 region「持有」调用方通道，{@code release()} 时由 Netty 关闭它
 * （{@code DefaultFileRegion.deallocate}），用例据此断言并在 finally 兜底关闭。
 */
class ZeroCopyResponseTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void fileConstructorUsesWholeFile() throws IOException {
        ZeroCopyResponse resp = new ZeroCopyResponse(write("z.bin", "abcdef").toFile());
        FileRegion region = resp.getFileRegion();
        try {
            assertEquals(0L, region.position());
            assertEquals(6L, region.count());
            assertEquals(0L, region.transferred());
            assertEquals("6", resp.headers().get(HttpHeaderNames.CONTENT_LENGTH));
            assertEquals(ZeroCopyResponseWriter.class, resp.getWriterType());
        } finally {
            region.release();
        }
    }

    @Test
    @DisplayName("HS-20 回归：Content-Length 与 FileRegion count 在 offset≠0 时同为传输字节数")
    void contentLengthEqualsRegionCountWithNonZeroOffset() throws IOException {
        ZeroCopyResponse resp = new ZeroCopyResponse(write("z.bin", "abcdef").toFile(), 2L, 5L);
        FileRegion region = resp.getFileRegion();
        try {
            assertEquals(2L, region.position());
            assertEquals(5L, region.count(), "region 从偏移 2 起请求传 5 字节");
            // 头声明与 region count 同为 count（5）；自偏移 2 起文件只剩 4 字节可读，
            // transferTo 到文件尾自然截断，实际写出字节数由真实下载用例侧裁决。
            assertEquals("5", resp.headers().get(HttpHeaderNames.CONTENT_LENGTH),
                    "Content-Length 取传输字节数，与 region 请求的 count 一致");
        } finally {
            region.release();
        }
    }

    @Test
    void negativePositionOrCountRejected() throws IOException {
        Path file = write("z.bin", "abcdef");
        assertThrows(IllegalArgumentException.class, () -> new ZeroCopyResponse(file.toFile(), -1L, 5L));
        assertThrows(IllegalArgumentException.class, () -> new ZeroCopyResponse(file.toFile(), 0L, -1L));
    }

    @Test
    void fileChannelConstructorUsesChannelSize() throws IOException {
        FileChannel channel = FileChannel.open(write("z.bin", "abcdef"), StandardOpenOption.READ);
        ZeroCopyResponse resp = new ZeroCopyResponse(channel, "z.bin");
        FileRegion region = resp.getFileRegion();
        try {
            assertEquals(0L, region.position());
            assertEquals(6L, region.count());
            assertEquals("6", resp.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        } finally {
            region.release();
            channel.close();
        }
    }

    @Test
    void fileChannelConstructorReleasesCallerChannelOnRelease() throws IOException {
        FileChannel channel = FileChannel.open(write("z.bin", "abcdef"), StandardOpenOption.READ);
        ZeroCopyResponse resp = new ZeroCopyResponse(channel, 0L, 6L, "z.bin");
        FileRegion region = resp.getFileRegion();
        try {
            assertEquals("6", resp.headers().get(HttpHeaderNames.CONTENT_LENGTH));
            assertEquals("attachment; filename=\"z.bin\"",
                    resp.headers().get(HttpHeaderNames.CONTENT_DISPOSITION));
            assertTrue(channel.isOpen());

            region.release();

            assertFalse(channel.isOpen(), "region 释放时必须关闭它持有的调用方 FileChannel（资源释放点）");
            // D1 最小编译修复：isOpen() 在 DefaultFileRegion 类上而非 FileRegion 接口（netty 4.2.12 sources jar 直证），下转后语义不变
            assertFalse(((DefaultFileRegion) region).isOpen());
        } finally {
            channel.close(); // 兜底：断言若在 release 之前失败，仍不泄漏句柄；二次 close 幂等
        }
    }

    @Test
    void channelConstructorWithoutFilenameUsesUuid() throws IOException {
        FileChannel channel = FileChannel.open(write("z.bin", "abcdef"), StandardOpenOption.READ);
        ZeroCopyResponse resp = new ZeroCopyResponse(channel);
        FileRegion region = resp.getFileRegion();
        try {
            String disposition = resp.headers().get(HttpHeaderNames.CONTENT_DISPOSITION);
            String prefix = "attachment; filename=\"";
            assertTrue(disposition.startsWith(prefix), disposition);
            String filename = disposition.substring(prefix.length(), disposition.length() - 1);
            assertDoesNotThrow(() -> UUID.fromString(filename), "自动文件名应是 UUID: " + filename);
        } finally {
            region.release();
            channel.close();
        }
    }

    @Test
    void missingFileThrows() {
        assertThrows(ResponseHandleException.class,
                () -> new ZeroCopyResponse(tempDir.resolve("nope.bin").toFile(), 0L, 1L));
    }
}
