package top.turboweb.http.orchestrator.writer;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.http.connection.InternalHttpConnection;
import top.turboweb.http.response.InternalSseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link SseResponseWriter} 的行为锚：
 * 内部发射器仅初始化（结果不可监听、无凭据）、错型经模板兜底记日志并关闭连接，与旧 SSE 策略语义一致。
 */
class SseResponseWriterTest {

    @Test
    void internalEmitterIsInitializedAndReturnsNotListenableResult() {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection session = new InternalHttpConnection(channel);
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
                .write(plain, new InternalHttpConnection(channel));

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
