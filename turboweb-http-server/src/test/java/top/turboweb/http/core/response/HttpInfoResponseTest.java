package top.turboweb.http.core.response;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.config.GlobalConfig;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link HttpInfoResponse} 的体/长度/类型/Cookie 快捷写法（spec §5：response）。
 */
class HttpInfoResponseTest {

    @Test
    void bareResponseCarriesStatusAndVersion() {
        HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
        try {
            assertEquals(200, response.status().code());
            assertEquals(HttpVersion.HTTP_1_1, response.protocolVersion());
            assertEquals(0, response.content().readableBytes());
        } finally {
            response.release();
        }
    }

    @Test
    void statusAndBodyConstructorKeepsProvidedBuffer() {
        HttpInfoResponse response = new HttpInfoResponse(
                HttpResponseStatus.CREATED, Unpooled.wrappedBuffer(new byte[]{1, 2, 3}));
        try {
            assertEquals(201, response.status().code());
            assertEquals(3, response.content().readableBytes());
        } finally {
            response.release();
        }
    }

    @Test
    void stringContentIsEncodedWithGivenCharsetAndUpdatesLength() {
        HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
        try {
            response.setContent("你好", StandardCharsets.UTF_8);
            assertEquals("你好", response.content().toString(StandardCharsets.UTF_8));
            assertEquals("你好".getBytes(StandardCharsets.UTF_8).length,
                    response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
        } finally {
            response.release();
        }
    }

    @Test
    void stringContentWithoutCharsetUsesGlobalResponseCharset() {
        Charset charset = GlobalConfig.getResponseCharset();
        HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
        try {
            response.setContent("你好");
            assertEquals("你好", response.content().toString(charset));
            assertEquals("你好".getBytes(charset).length,
                    response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
        } finally {
            response.release();
        }
    }

    @Test
    void settingContentAgainReplacesTheBody() {
        HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
        try {
            response.setContent("aaaa", StandardCharsets.UTF_8);
            response.setContent(new byte[]{1, 2, 3});
            assertEquals(3, response.content().readableBytes(), "重复 setContent 必须清空旧内容");
            assertEquals(3, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
            assertEquals(1, response.content().getByte(0));
            assertEquals(3, response.content().getByte(2));
        } finally {
            response.release();
        }
    }

    @Test
    void contentTypeIsWrittenToHeader() {
        HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
        try {
            response.setContentType("application/xml;charset=utf-8");
            assertEquals("application/xml;charset=utf-8",
                    response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        } finally {
            response.release();
        }
    }

    @Test
    void firstCookieIsSetAndSecondIsAppended() {
        HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
        try {
            response.setCookie("a", "1");
            assertEquals(List.of("a=1"), response.headers().getAll(HttpHeaderNames.SET_COOKIE));
            response.setCookie("b", "2");
            assertEquals(List.of("a=1", "b=2"),
                    response.headers().getAll(HttpHeaderNames.SET_COOKIE),
                    "已有 Set-Cookie 时必须 add 追加而不是 set 覆盖");
        } finally {
            response.release();
        }
    }
}
