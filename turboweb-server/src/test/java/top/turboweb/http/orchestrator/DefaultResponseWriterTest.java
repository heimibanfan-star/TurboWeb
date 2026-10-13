package top.turboweb.http.orchestrator;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.connect.InternalConnectSession;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultResponseWriter} 的行为锚：Content-Length 补齐规则与写出动作，
 * 语义与旧默认响应策略逐条一致；写出结果恒可监听并携带头写出凭据。
 */
class DefaultResponseWriterTest {

    private static DefaultFullHttpResponse full(String body) {
        DefaultFullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.content().writeCharSequence(body, StandardCharsets.UTF_8);
        return response;
    }

    @Test
    void fullResponseWithoutContentLengthGetsBodyLength() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultFullHttpResponse response = full("abc");

        ResponseWriter.WriteResult result = new DefaultResponseWriter()
                .write(response, new InternalConnectSession(channel));

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        assertTrue(result.channelFuture().isSuccess());
        assertEquals("3", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertSame(response, channel.readOutbound());
        channel.finishAndReleaseAll();
    }

    @Test
    void existingContentLengthIsNotOverwritten() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultFullHttpResponse response = full("abc");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 99);

        ResponseWriter.WriteResult result = new DefaultResponseWriter()
                .write(response, new InternalConnectSession(channel));

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        assertTrue(result.channelFuture().isSuccess());
        assertEquals("99", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertSame(response, channel.readOutbound());
        channel.finishAndReleaseAll();
    }

    @Test
    void nonFullResponseGetsZeroContentLength() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        ResponseWriter.WriteResult result = new DefaultResponseWriter()
                .write(response, new InternalConnectSession(channel));

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        assertTrue(result.channelFuture().isSuccess());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertSame(response, channel.readOutbound());
        channel.finishAndReleaseAll();
    }
}
