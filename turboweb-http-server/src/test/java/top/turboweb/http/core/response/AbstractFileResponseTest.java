package top.turboweb.http.core.response;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.turboweb.commons.exception.TurboFileException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AbstractFileResponse} 的文件校验矩阵与响应头初始化。
 * 抽象类不可直接实例化，用同包探针子类暴露两个 protected 构造器。
 */
class AbstractFileResponseTest {

    private static final class ProbeFileResponse extends AbstractFileResponse {
        ProbeFileResponse(HttpResponseStatus status, File file, Charset charset) {
            super(status, file, charset);
        }

        ProbeFileResponse(HttpResponseStatus status, String filename, Charset charset) {
            super(status, filename, charset);
        }
    }

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void fileConstructorSetsOctetStreamAndEncodedDisposition() throws IOException {
        Path file = write("my report.txt", "x");

        ProbeFileResponse resp = new ProbeFileResponse(HttpResponseStatus.CREATED, file.toFile(), StandardCharsets.UTF_8);

        assertEquals(HttpVersion.HTTP_1_1, resp.protocolVersion());
        assertEquals(HttpResponseStatus.CREATED, resp.status());
        assertEquals("application/octet-stream", resp.headers().get(HttpHeaderNames.CONTENT_TYPE));
        // URLEncoder 会把空格编成 '+'，产品必须再替换成 %20，否则浏览器拿到带 '+' 的文件名
        assertEquals("attachment; filename=\"my%20report.txt\"",
                resp.headers().get(HttpHeaderNames.CONTENT_DISPOSITION));
        assertEquals("keep-alive", resp.headers().get(HttpHeaderNames.CONNECTION));
    }

    @Test
    void filenameConstructorSkipsDiskVerification() {
        ProbeFileResponse resp = new ProbeFileResponse(
                HttpResponseStatus.OK, "no-such-file.txt", StandardCharsets.UTF_8);

        assertEquals("attachment; filename=\"no-such-file.txt\"",
                resp.headers().get(HttpHeaderNames.CONTENT_DISPOSITION));
    }

    @Test
    void nonexistentFileThrows() {
        Path missing = tempDir.resolve("missing.txt");

        TurboFileException ex = assertThrows(TurboFileException.class,
                () -> new ProbeFileResponse(HttpResponseStatus.OK, missing.toFile(), StandardCharsets.UTF_8));

        assertEquals("文件不存在," + missing.toFile().getAbsolutePath(), ex.getMessage());
    }

    @Test
    void directoryThrows() {
        TurboFileException ex = assertThrows(TurboFileException.class,
                () -> new ProbeFileResponse(HttpResponseStatus.OK, tempDir.toFile(), StandardCharsets.UTF_8));

        assertEquals("文件是文件夹," + tempDir.toFile().getAbsolutePath(), ex.getMessage());
    }

    @Test
    void setContentTypeOverridesHeader() {
        ProbeFileResponse resp = new ProbeFileResponse(HttpResponseStatus.OK, "x.txt", StandardCharsets.UTF_8);

        resp.setContentType(ContentType.create("image/png"));

        assertEquals("image/png", resp.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }
}
