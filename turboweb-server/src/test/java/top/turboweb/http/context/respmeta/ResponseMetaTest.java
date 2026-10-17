package top.turboweb.http.context.respmeta;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import top.turboweb.rest.core.ContentType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link DefaultResponseMeta} 的状态码/内容类型读写（spec §5：context 行）。
 */
class ResponseMetaTest {

    @Test
    void freshMetaHasNeitherStatusNorContentType() {
        DefaultResponseMeta meta = new DefaultResponseMeta();
        assertNull(meta.getStatus());
        assertNull(meta.getContentType());
    }

    @Test
    void intStatusIsConvertedToStatusObject() {
        DefaultResponseMeta meta = new DefaultResponseMeta();
        meta.status(404);
        assertEquals(HttpResponseStatus.NOT_FOUND, meta.getStatus());
        assertEquals(404, meta.getStatus().code());
    }

    @Test
    void statusObjectIsStoredByReference() {
        DefaultResponseMeta meta = new DefaultResponseMeta();
        HttpResponseStatus status = HttpResponseStatus.CREATED;
        meta.status(status);
        assertSame(status, meta.getStatus());
    }

    @Test
    void contentTypeIsStoredByReference() {
        DefaultResponseMeta meta = new DefaultResponseMeta();
        ContentType contentType = ContentType.APPLICATION_JSON;
        meta.contentType(contentType);
        assertSame(contentType, meta.getContentType());
    }

    @Test
    void laterStatusOverwritesEarlierOne() {
        DefaultResponseMeta meta = new DefaultResponseMeta();
        meta.status(500);
        meta.status(HttpResponseStatus.ACCEPTED);
        assertEquals(202, meta.getStatus().code());
        meta.status(204);
        assertEquals(204, meta.getStatus().code());
    }
}
