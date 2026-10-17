package top.turboweb.http.context;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.MemoryFileUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.turboweb.rest.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DefaultHttpFile} 委托 Netty 上传段的读取面：内容通道每次自起始处读取，
 * 元信息按上传段声明透出，Content-Type 在构造期完成 MIME 与 charset 解析。
 */
class DefaultHttpFileTest {

    private final List<FileUpload> uploads = new ArrayList<>();

    @AfterEach
    void deleteUploads() {
        for (FileUpload upload : uploads) {
            upload.delete();
        }
    }

    /** 建立内存驻留的上传段并登记释放 */
    private MemoryFileUpload memoryUpload(String filename, String contentType, byte[] content) throws IOException {
        MemoryFileUpload upload = new MemoryFileUpload("file", filename, contentType, null,
                StandardCharsets.UTF_8, content.length);
        upload.setContent(Unpooled.wrappedBuffer(content));
        uploads.add(upload);
        return upload;
    }

    /** 以固定原始 Content-Type 取值建立空上传段，用于解析口径的用例 */
    private MemoryFileUpload stagingUpload(String contentType) {
        MemoryFileUpload upload = new MemoryFileUpload("file", "a.txt", "application/octet-stream", null,
                StandardCharsets.UTF_8, 0) {
            @Override
            public String getContentType() {
                return contentType;
            }
        };
        uploads.add(upload);
        return upload;
    }

    @Test
    void readAllReturnsWholeContent() throws IOException {
        DefaultHttpFile file = new DefaultHttpFile(memoryUpload("a.txt", "text/plain", new byte[]{'a', 'b', 'c'}));

        assertArrayEquals(new byte[]{'a', 'b', 'c'}, file.readAll());
    }

    @Test
    void inputStreamReadsFromBeginningOnEveryCall() throws IOException {
        DefaultHttpFile file = new DefaultHttpFile(memoryUpload("a.txt", "text/plain", new byte[]{'a', 'b', 'c'}));

        try (InputStream first = file.inputStream()) {
            assertEquals('a', first.read());
        }
        try (InputStream second = file.inputStream()) {
            assertArrayEquals(new byte[]{'a', 'b', 'c'}, second.readAllBytes());
        }
    }

    @Test
    void inputStreamIsEmptyBeforeContentArrives() throws IOException {
        MemoryFileUpload upload = new MemoryFileUpload("file", "a.txt", "application/octet-stream", null,
                StandardCharsets.UTF_8, 0);
        uploads.add(upload);
        DefaultHttpFile file = new DefaultHttpFile(upload);

        try (InputStream in = file.inputStream()) {
            assertEquals(-1, in.read());
        }
        assertArrayEquals(new byte[0], file.readAll());
    }

    @Test
    void inputStreamReadsDiskBackedContent(@TempDir Path tempDir) throws IOException {
        DiskFileUpload upload = new DiskFileUpload("file", "a.bin", "application/octet-stream", null,
                StandardCharsets.UTF_8, 3, tempDir.toString(), false);
        uploads.add(upload);
        upload.setContent(Unpooled.wrappedBuffer(new byte[]{'x', 'y', 'z'}));
        DefaultHttpFile file = new DefaultHttpFile(upload);

        try (InputStream in = file.inputStream()) {
            assertArrayEquals(new byte[]{'x', 'y', 'z'}, in.readAllBytes());
        }
        assertArrayEquals(new byte[]{'x', 'y', 'z'}, file.readAll());
    }

    @Test
    void filenameReturnsOriginalName() throws IOException {
        DefaultHttpFile file = new DefaultHttpFile(memoryUpload("report.pdf", "application/pdf", new byte[]{1}));

        assertEquals("report.pdf", file.filename());
    }

    @Test
    void lengthReturnsContentLength() throws IOException {
        DefaultHttpFile file = new DefaultHttpFile(memoryUpload("a.txt", "text/plain", new byte[]{'a', 'b', 'c'}));

        assertEquals(3L, file.length());
    }

    @Test
    void contentTypeParsesMimeTypeAndDeclaredCharset() {
        ContentType type = new DefaultHttpFile(stagingUpload("text/plain; charset=UTF-8")).contentType();

        assertNotNull(type);
        assertEquals("text/plain", type.getMimeType());
        assertEquals(StandardCharsets.UTF_8, type.getCharset());
    }

    @Test
    void contentTypeLeavesCharsetUnsetWhenAbsent() {
        ContentType type = new DefaultHttpFile(stagingUpload("image/png")).contentType();

        assertNotNull(type);
        assertEquals("image/png", type.getMimeType());
        assertNull(type.getCharset());
    }

    @Test
    void contentTypeFindsCharsetAfterOtherParameters() {
        ContentType type = new DefaultHttpFile(
                stagingUpload("text/plain; format=flowed; charset=UTF-8")).contentType();

        assertNotNull(type);
        assertEquals("text/plain", type.getMimeType());
        assertEquals(StandardCharsets.UTF_8, type.getCharset());
    }

    @Test
    void contentTypeMatchesCharsetParameterCaseInsensitively() {
        ContentType type = new DefaultHttpFile(stagingUpload("text/plain; Charset=UTF-8")).contentType();

        assertNotNull(type);
        assertEquals(StandardCharsets.UTF_8, type.getCharset());
    }

    @Test
    void contentTypeStripsQuotesAroundCharsetName() {
        ContentType type = new DefaultHttpFile(stagingUpload("text/plain; charset=\"UTF-8\"")).contentType();

        assertNotNull(type);
        assertEquals(StandardCharsets.UTF_8, type.getCharset());
    }

    @Test
    void contentTypeKeepsMimeTypeWhenCharsetUnknown() {
        ContentType type = new DefaultHttpFile(
                stagingUpload("text/plain; charset=no-such-charset")).contentType();

        assertNotNull(type);
        assertEquals("text/plain", type.getMimeType());
        assertNull(type.getCharset());
    }

    @Test
    void contentTypeIgnoresEmptyCharsetName() {
        ContentType type = new DefaultHttpFile(stagingUpload("text/plain; charset= ")).contentType();

        assertNotNull(type);
        assertEquals("text/plain", type.getMimeType());
        assertNull(type.getCharset());
    }

    @Test
    void contentTypeReturnsNullWhenNotDeclared() {
        assertNull(new DefaultHttpFile(stagingUpload(null)).contentType());
    }

    @Test
    void contentTypeReturnsNullWhenDeclaredValueIsBlank() {
        assertNull(new DefaultHttpFile(stagingUpload("  ")).contentType());
    }

    @Test
    void contentTypeReturnsNullWhenMimeTypeIsEmpty() {
        assertNull(new DefaultHttpFile(stagingUpload("; charset=UTF-8")).contentType());
    }

    @Test
    void constructorRejectsNullFileUpload() {
        assertThrows(NullPointerException.class, () -> new DefaultHttpFile(null));
    }
}
