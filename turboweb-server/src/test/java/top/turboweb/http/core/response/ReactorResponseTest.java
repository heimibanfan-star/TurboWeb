package top.turboweb.http.core.response;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.turboweb.rest.ContentType;
import top.turboweb.http.orchestrator.ReactorResponseWriter;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link ReactorResponse} 的字符集显式口径与 Flux 包装（reactor 同步订阅，无时序）。
 * 内容类型带字符集时 header 与 getCharset 同源写明；不带时只写媒体类型、getCharset 为空，
 * charset 段留待结果转化环节按缺省字符集补写。
 * 依赖来自本模块 api 声明的 {@code reactor-core} 与仓内 {@code core.constant.ContentType}。
 */
class ReactorResponseTest {

    @Test
    void defaultConstructorLeavesHtmlWithoutCharset() {
        ReactorResponse resp = new ReactorResponse(Flux.empty());

        assertEquals("text/html", resp.headers().get(HttpHeaderNames.CONTENT_TYPE),
                "默认内容类型不带 charset 段，补码归结果转化环节");
        assertNull(resp.getCharset());
        assertEquals(HttpResponseStatus.OK, resp.status());
        assertEquals(ReactorResponseWriter.class, resp.getWriterType());
    }

    @Test
    void explicitCharsetIsWrittenOut() {
        ReactorResponse resp = new ReactorResponse(Flux.empty(),
                ContentType.create("application/json", StandardCharsets.ISO_8859_1));

        assertEquals("application/json;charset=ISO-8859-1", resp.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals(StandardCharsets.ISO_8859_1, resp.getCharset());
    }

    @Test
    void contentTypeWithoutCharsetStaysPending() {
        ReactorResponse resp = new ReactorResponse(Flux.empty(), ContentType.create("text/plain"));

        assertNull(resp.getCharset());
        assertEquals("text/plain", resp.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }

    @Test
    @DisplayName("HS-21 回归：setContentType 同步 charset 字段，getCharset 与 header 声明一致")
    void setContentTypeSyncsCharsetFieldWithHeader() {
        ReactorResponse resp = new ReactorResponse(Flux.empty(),
                ContentType.create("text/html", StandardCharsets.UTF_8));

        resp.setContentType(ContentType.create("application/octet-stream", StandardCharsets.ISO_8859_1));

        assertEquals("application/octet-stream;charset=ISO-8859-1",
                resp.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals(StandardCharsets.ISO_8859_1, resp.getCharset(),
                "charset 字段随 setContentType 同步，getCharset() 与 header 同为 ISO-8859-1");

        // 另一支：新内容类型不带字符集时，字段为空、header 只写媒体类型
        resp.setContentType(ContentType.create("text/plain"));
        assertEquals("text/plain", resp.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertNull(resp.getCharset(),
                "无字符集的内容类型不回填任何默认，补码归结果转化环节");
    }

    @Test
    void fluxWrapsPublisherAndEmitsByteBufs() {
        ByteBuf a = Unpooled.wrappedBuffer("ab".getBytes(StandardCharsets.UTF_8));
        ByteBuf b = Unpooled.wrappedBuffer("cd".getBytes(StandardCharsets.UTF_8));
        ReactorResponse resp = new ReactorResponse(Flux.just(a, b),
                ContentType.create("text/plain", StandardCharsets.UTF_8));

        List<ByteBuf> emitted = resp.getFlux().collectList().block();
        try {
            assertNotNull(emitted);
            assertEquals(2, emitted.size());
            assertEquals("ab", emitted.get(0).toString(StandardCharsets.UTF_8));
            assertEquals("cd", emitted.get(1).toString(StandardCharsets.UTF_8));
        } finally {
            if (emitted != null) {
                emitted.forEach(buf -> {
                    if (buf.refCnt() > 0) {
                        buf.release();
                    }
                });
            }
        }
    }
}
