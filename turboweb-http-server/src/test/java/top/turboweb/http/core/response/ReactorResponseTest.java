package top.turboweb.http.core.response;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.turboweb.commons.config.GlobalConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link ReactorResponse} 的字符集回退与 Flux 包装（reactor 同步订阅，无时序）。
 * 依赖来自约定插件 {@code api(reactor-netty)} → {@code reactor-core}、{@code api(httpcore5)}（见任务头）。
 */
class ReactorResponseTest {

    @Test
    void defaultConstructorFallsBackToGlobalCharset() {
        ReactorResponse resp = new ReactorResponse(Flux.empty());

        assertEquals("text/html;charset=" + GlobalConfig.getResponseCharset().name(),
                resp.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals(GlobalConfig.getResponseCharset(), resp.getCharset());
        assertEquals(HttpResponseStatus.OK, resp.status());
        assertEquals(InternalCallResponse.InternalCallType.REACTOR, resp.getType());
    }

    @Test
    void explicitCharsetOverridesGlobal() {
        ReactorResponse resp = new ReactorResponse(Flux.empty(),
                ContentType.create("application/json", StandardCharsets.ISO_8859_1));

        assertEquals("application/json;charset=ISO-8859-1", resp.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals(StandardCharsets.ISO_8859_1, resp.getCharset());
    }

    @Test
    void contentTypeWithoutCharsetFallsBackToGlobal() {
        ReactorResponse resp = new ReactorResponse(Flux.empty(), ContentType.create("text/plain"));

        assertEquals(GlobalConfig.getResponseCharset(), resp.getCharset());
        assertEquals("text/plain;charset=" + GlobalConfig.getResponseCharset().name(),
                resp.headers().get(HttpHeaderNames.CONTENT_TYPE));
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

        // 三元回退的另一支：新内容类型不带字符集时，字段与 header 均回落到全局默认
        resp.setContentType(ContentType.create("text/plain"));
        assertEquals("text/plain;charset=" + GlobalConfig.getResponseCharset().name(),
                resp.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals(GlobalConfig.getResponseCharset(), resp.getCharset(),
                "无字符集的内容类型回退全局默认，字段与 header 一致");
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
