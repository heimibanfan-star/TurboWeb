package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.core.response.InternalSseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.serializer.JacksonJsonSerializer;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SseResponseWriter} 的行为锚：SseResponse 写头并触发回调、
 * 内部发射器仅初始化并返回 null、错型经模板兜底 500，与旧 SSE 策略语义一致。
 */
class SseResponseWriterTest {

    @Test
    void sseResponseWritesHeaderAndStartsCallback() {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalConnectSession session = new InternalConnectSession(channel);
        AtomicBoolean callbackRan = new AtomicBoolean(false);
        SseResponse response = new SseResponse(
                HttpResponseStatus.OK, new DefaultHttpHeaders(), session, new JacksonJsonSerializer());
        response.setSseCallback(connectSession -> callbackRan.set(true));

        ChannelFuture future = new SseResponseWriter().write(response, session);

        assertTrue(future.isSuccess(), "返回的应是响应头写出的凭据");
        assertTrue(callbackRan.get(), "写头后必须触发 SSE 回调");
        assertSame(response, channel.readOutbound());
        channel.finishAndReleaseAll();
    }

    @Test
    void internalEmitterIsInitializedAndReturnsNullFuture() {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalConnectSession session = new InternalConnectSession(channel);
        InternalSseEmitter emitter = new InternalSseEmitter(session, 10);

        ChannelFuture future = new SseResponseWriter().write(emitter, session);

        assertNull(future, "发射器分支无写出凭据");
        assertSame(emitter, channel.readOutbound(), "initSse 必须已发出响应头");
        channel.finishAndReleaseAll();
    }

    @Test
    void wrongTypeIsCaughtByTemplateAndWrittenAs500() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultFullHttpResponse plain =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        ChannelFuture future = new SseResponseWriter()
                .write(plain, new InternalConnectSession(channel));

        assertTrue(future.isSuccess(), "模板方法把类型异常兜成错误响应写回");
        DefaultFullHttpResponse err = (DefaultFullHttpResponse) channel.readOutbound();
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, err.status());
        plain.release();
        err.release();
        channel.finishAndReleaseAll();
    }
}
