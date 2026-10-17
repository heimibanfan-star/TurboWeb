package top.turboweb.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ContentType} 值对象：创建、取值与头部格式化，以及全部标准媒体类型常量。
 */
class ContentTypeTest {

    private static void assertConstant(ContentType actual, String mimeType, Charset charset) {
        assertEquals(mimeType, actual.getMimeType());
        assertEquals(charset, actual.getCharset());
        String expectedToString = charset == null ? mimeType : mimeType + "; charset=" + charset.name();
        assertEquals(expectedToString, actual.toString());
    }

    @Test
    void createWithoutCharsetKeepsMimeAndNullCharset() {
        ContentType ct = ContentType.create("text/plain");
        assertEquals("text/plain", ct.getMimeType());
        assertNull(ct.getCharset());
        // 无字符集时格式化只输出 MIME
        assertEquals("text/plain", ct.toString());
    }

    @Test
    void createWithCharsetKeepsBothFields() {
        ContentType ct = ContentType.create("application/json", StandardCharsets.ISO_8859_1);
        assertEquals("application/json", ct.getMimeType());
        assertEquals(StandardCharsets.ISO_8859_1, ct.getCharset());
        assertEquals("application/json; charset=ISO-8859-1", ct.toString());
    }

    @Test
    void createWithExplicitNullCharsetEqualsSingleArgForm() {
        assertConstant(ContentType.create("image/gif", null), "image/gif", null);
    }

    @Test
    void createRejectsNullMime() {
        NullPointerException single = assertThrows(NullPointerException.class,
                () -> ContentType.create(null));
        assertEquals("MIME type", single.getMessage());
        NullPointerException two = assertThrows(NullPointerException.class,
                () -> ContentType.create(null, StandardCharsets.UTF_8));
        assertEquals("MIME type", two.getMessage());
    }

    @Test
    @DisplayName("自定义 MIME 不校验内部空白，原样承载")
    void createDoesNotValidateInnerBlanks() {
        assertConstant(ContentType.create("text plain"), "text plain", null);
    }

    @Test
    void applicationFamilyConstantsMatchSpec() {
        assertConstant(ContentType.APPLICATION_ATOM_XML, "application/atom+xml", StandardCharsets.UTF_8);
        assertConstant(ContentType.APPLICATION_FORM_URLENCODED, "application/x-www-form-urlencoded", StandardCharsets.ISO_8859_1);
        assertConstant(ContentType.APPLICATION_JSON, "application/json", StandardCharsets.UTF_8);
        assertConstant(ContentType.APPLICATION_NDJSON, "application/x-ndjson", StandardCharsets.UTF_8);
        assertConstant(ContentType.APPLICATION_OCTET_STREAM, "application/octet-stream", null);
        assertConstant(ContentType.APPLICATION_PDF, "application/pdf", StandardCharsets.UTF_8);
        assertConstant(ContentType.APPLICATION_PROBLEM_JSON, "application/problem+json", StandardCharsets.UTF_8);
        assertConstant(ContentType.APPLICATION_PROBLEM_XML, "application/problem+xml", StandardCharsets.UTF_8);
        assertConstant(ContentType.APPLICATION_RSS_XML, "application/rss+xml", StandardCharsets.UTF_8);
        assertConstant(ContentType.APPLICATION_SOAP_XML, "application/soap+xml", StandardCharsets.UTF_8);
        assertConstant(ContentType.APPLICATION_SVG_XML, "application/svg+xml", null);
        assertConstant(ContentType.APPLICATION_XHTML_XML, "application/xhtml+xml", null);
        assertConstant(ContentType.APPLICATION_XML, "application/xml", StandardCharsets.UTF_8);
    }

    @Test
    void imageFamilyConstantsMatchSpec() {
        assertConstant(ContentType.IMAGE_BMP, "image/bmp", null);
        assertConstant(ContentType.IMAGE_GIF, "image/gif", null);
        assertConstant(ContentType.IMAGE_JPEG, "image/jpeg", null);
        assertConstant(ContentType.IMAGE_PNG, "image/png", null);
        assertConstant(ContentType.IMAGE_SVG, "image/svg+xml", null);
        assertConstant(ContentType.IMAGE_TIFF, "image/tiff", null);
        assertConstant(ContentType.IMAGE_WEBP, "image/webp", null);
    }

    @Test
    void multipartFamilyConstantsMatchSpec() {
        assertConstant(ContentType.MULTIPART_FORM_DATA, "multipart/form-data", StandardCharsets.ISO_8859_1);
        assertConstant(ContentType.MULTIPART_MIXED, "multipart/mixed", StandardCharsets.ISO_8859_1);
        assertConstant(ContentType.MULTIPART_RELATED, "multipart/related", StandardCharsets.ISO_8859_1);
    }

    @Test
    void textFamilyAndWildcardConstantsMatchSpec() {
        assertConstant(ContentType.TEXT_EVENT_STREAM, "text/event-stream", StandardCharsets.UTF_8);
        assertConstant(ContentType.TEXT_HTML, "text/html", StandardCharsets.UTF_8);
        assertConstant(ContentType.TEXT_MARKDOWN, "text/markdown", StandardCharsets.UTF_8);
        assertConstant(ContentType.TEXT_PLAIN, "text/plain", StandardCharsets.UTF_8);
        assertConstant(ContentType.TEXT_XML, "text/xml", StandardCharsets.UTF_8);
        assertConstant(ContentType.WILDCARD, "*/*", null);
    }

    @Test
    void defaultAliasesReferToSameInstances() {
        assertSame(ContentType.TEXT_PLAIN, ContentType.DEFAULT_TEXT);
        assertSame(ContentType.APPLICATION_OCTET_STREAM, ContentType.DEFAULT_BINARY);
    }
}
