package top.turboweb.http.core.response;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.orchestrator.SseResponseWriter;
import top.turboweb.http.testkit.RecordingConnectSession;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SseResponse} 的头部设置与回调/Flux 触发（reactor 同步订阅，无时序）。
 *
 * <p>reactor-core 经约定插件的 {@code api(reactor-netty)} → {@code reactor-core} 传递进本模块
 * （见任务头「依赖结论」），产品类 SseResponse 自身就 import 了它。
 */
class SseResponseTest {

    private static SseResponse newResponse(RecordingConnectSession session) {
        return new SseResponse(HttpResponseStatus.OK, new DefaultHttpHeaders(), session, null);
    }

    @Test
    void constructorSetsSseHeadersWithGlobalCharset() {
        RecordingConnectSession session = new RecordingConnectSession();
        SseResponse resp = newResponse(session);

        assertEquals("text/event-stream;charset=" + GlobalConfig.getResponseCharset().name(),
                resp.headers().get(HttpHeaderNames.CONTENT_TYPE));
        assertEquals("no-cache", resp.headers().get(HttpHeaderNames.CACHE_CONTROL));
        assertEquals("keep-alive", resp.headers().get(HttpHeaderNames.CONNECTION));
        assertEquals("chunked", resp.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
        assertTrue(HttpUtil.isTransferEncodingChunked(resp));
        assertEquals(HttpResponseStatus.OK, resp.status());
    }

    @Test
    @DisplayName("connectSession 为 null 时由 assert 快速失败（Gradle Test 默认 -ea）")
    void nullConnectSessionRejectedByAssertion() {
        assertThrows(AssertionError.class,
                () -> new SseResponse(HttpResponseStatus.OK, new DefaultHttpHeaders(), null, null));
    }

    @Test
    void startSseWithoutCallbackIsNoop() {
        RecordingConnectSession session = new RecordingConnectSession();
        SseResponse resp = newResponse(session);

        resp.startSse();

        assertTrue(session.sentMessages().isEmpty());
        assertEquals(0, session.closeCount());
    }

    @Test
    void sseCallbackReceivesConnectSession() {
        RecordingConnectSession session = new RecordingConnectSession();
        SseResponse resp = newResponse(session);
        List<ConnectSession> captured = new ArrayList<>();

        resp.setSseCallback(captured::add);
        resp.startSse();

        assertEquals(1, captured.size());
        assertSame(session, captured.get(0));
    }

    @Test
    void fluxIsForwardedVerbatimAndClosesOnCompletion() {
        RecordingConnectSession session = new RecordingConnectSession();
        SseResponse resp = newResponse(session);

        resp.setSseCallback(Flux.just("a", "b"));
        resp.startSse();

        assertEquals(List.of("a", "b"), session.sentMessages(), "两参重载原样转发，不加 SSE 帧前缀");
        assertEquals(1, session.closeCount(), "默认 onFinally = ConnectSession::close，完成即关连接");
    }

    @Test
    void setFluxWrapsDataFrames() {
        RecordingConnectSession session = new RecordingConnectSession();
        SseResponse resp = newResponse(session);

        resp.setFlux(Flux.just("a", "b"));
        resp.startSse();

        assertEquals(List.of("data:a\n\n", "data:b\n\n"), session.sentMessages());
        assertEquals(1, session.closeCount());
    }

    @Test
    void errorIsMappedByCustomHandler() {
        RecordingConnectSession session = new RecordingConnectSession();
        SseResponse resp = newResponse(session);
        List<String> finallyCalls = new ArrayList<>();

        resp.setSseCallback(Flux.<String>error(new IllegalStateException("boom")),
                err -> "custom:" + err.getMessage(),
                s -> finallyCalls.add("finally"));
        resp.startSse();

        assertEquals(List.of("custom:boom"), session.sentMessages());
        assertEquals(List.of("finally"), finallyCalls, "doFinally 在错误终止时也必须触发");
    }

    @Test
    void defaultErrorHandlerUsesErrorPrefix() {
        RecordingConnectSession session = new RecordingConnectSession();
        SseResponse resp = newResponse(session);

        resp.setSseCallback(Flux.<String>error(new IllegalStateException("boom")), null, s -> { });
        resp.startSse();

        assertEquals(List.of("error:boom"), session.sentMessages());
    }

    @Test
    void writerTypeIsSseResponseWriter() {
        assertEquals(SseResponseWriter.class, newResponse(new RecordingConnectSession()).getWriterType());
    }
}
