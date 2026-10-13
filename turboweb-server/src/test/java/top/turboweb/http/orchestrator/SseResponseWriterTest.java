package top.turboweb.http.orchestrator;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SseResponseWriter} 的行为锚：SseResponse 写头并触发回调（结果可监听）、
 * 内部发射器仅初始化（结果不可监听、无凭据）、错型经模板兜底记日志并关闭连接，与旧 SSE 策略语义一致。
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

        ResponseWriter.WriteResult result = new SseResponseWriter().write(response, session);

        assertEquals(ResponseWriter.WriteType.LISTENABLE, result.writeType());
        assertTrue(result.channelFuture().isSuccess(), "返回的应是响应头写出的凭据");
        assertTrue(callbackRan.get(), "写头后必须触发 SSE 回调");
        assertSame(response, channel.readOutbound());
        channel.finishAndReleaseAll();
    }

    @Test
    void internalEmitterIsInitializedAndReturnsNotListenableResult() {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalConnectSession session = new InternalConnectSession(channel);
        InternalSseEmitter emitter = new InternalSseEmitter(session, 10);

        ResponseWriter.WriteResult result = new SseResponseWriter().write(emitter, session);

        assertEquals(ResponseWriter.WriteType.NOT_LISTENABLE, result.writeType(),
                "发射器分支无写出凭据，结果不可监听");
        assertNull(result.channelFuture(), "不可监听结果的凭据恒为空");
        assertSame(emitter, channel.readOutbound(), "initSse 必须已发出响应头");
        channel.finishAndReleaseAll();
    }

    @Test
    void wrongTypeIsCaughtByTemplateAndClosesConnection() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultFullHttpResponse plain =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        ResponseWriter.WriteResult result = new SseResponseWriter()
                .write(plain, new InternalConnectSession(channel));

        assertEquals(ResponseWriter.WriteType.NOT_LISTENABLE, result.writeType(),
                "写出异常无凭据，结果不可监听");
        assertNull(result.channelFuture(), "不可监听结果的凭据恒为空");
        assertNull(channel.readOutbound(), "兜底不再写出错误页");
        channel.runPendingTasks();
        assertFalse(channel.isActive(), "写出异常必须关闭连接");
        plain.release();
        channel.finishAndReleaseAll();
    }
}
