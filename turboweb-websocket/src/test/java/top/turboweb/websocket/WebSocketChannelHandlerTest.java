package top.turboweb.websocket;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WebSocketChannelHandler} 的帧分派、会话获取与连接关闭：用同步执行器替身让分发在提交线程
 * 立即完成，逐帧核对业务回调、帧引用归还与异常传播。
 */
class WebSocketChannelHandlerTest {

    /** 与主码一致的会话附件键（同名 AttributeKey 全局唯一） */
    private static final AttributeKey<WebSocketSession> SESSION_KEY =
            AttributeKey.valueOf("top.turboweb.websocket.session");

    private RecordingWebSocketHandler business;

    private DirectMicroTaskExecutor executor;

    private WebSocketChannelHandler handler;

    private EmbeddedChannel channel;

    private RecordingWebSocketSession session;

    @BeforeEach
    void setUp() {
        business = new RecordingWebSocketHandler();
        executor = new DirectMicroTaskExecutor();
        handler = new WebSocketChannelHandler(business, executor);
        channel = new EmbeddedChannel(handler);
        session = new RecordingWebSocketSession();
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Test
    void constructorRejectsNullExecutor() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new WebSocketChannelHandler(business, null));

        assertEquals("microTaskExecutor must not be null", ex.getMessage());
    }

    @Test
    void textFrameIsDispatchedWithSession() {
        attachSession();
        TextWebSocketFrame frame = new TextWebSocketFrame("hello");

        channel.writeInbound(frame);

        assertEquals(List.of("text"), business.events);
        assertSame(session, business.lastSession);
        assertEquals("hello", business.payloads.get(0));
        assertEquals(1, executor.submissions);
        assertEquals(0, frame.refCnt(), "分发完成后帧引用必须归还");
    }

    @Test
    void binaryFrameIsDispatchedWithSession() {
        attachSession();
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(Unpooled.copiedBuffer(new byte[]{1, 2}));

        channel.writeInbound(frame);

        assertEquals(List.of("binary"), business.events);
        assertSame(session, business.lastSession);
        assertArrayEquals(new byte[]{1, 2}, (byte[]) business.payloads.get(0));
        assertEquals(0, frame.refCnt());
    }

    @Test
    void pingFrameIsDispatchedWithSession() {
        attachSession();
        PingWebSocketFrame frame = new PingWebSocketFrame(Unpooled.copiedBuffer(new byte[]{3}));

        channel.writeInbound(frame);

        assertEquals(List.of("ping"), business.events);
        assertSame(session, business.lastSession);
        assertArrayEquals(new byte[]{3}, (byte[]) business.payloads.get(0));
        assertEquals(0, frame.refCnt());
    }

    @Test
    void pongFrameIsDispatchedWithSession() {
        attachSession();
        PongWebSocketFrame frame = new PongWebSocketFrame(Unpooled.copiedBuffer(new byte[]{4}));

        channel.writeInbound(frame);

        assertEquals(List.of("pong"), business.events);
        assertSame(session, business.lastSession);
        assertArrayEquals(new byte[]{4}, (byte[]) business.payloads.get(0));
        assertEquals(0, frame.refCnt());
    }

    @Test
    void unsupportedFrameIsIgnoredAndReleased() {
        attachSession();
        ContinuationWebSocketFrame frame = new ContinuationWebSocketFrame(Unpooled.copiedBuffer(new byte[]{1}));

        channel.writeInbound(frame);

        assertTrue(business.events.isEmpty(), "文本、二进制、ping、pong 之外的帧不应触发业务回调");
        assertEquals(1, executor.submissions, "未知帧同样经执行器提交分派");
        assertEquals(0, frame.refCnt());
    }

    @Test
    void missingSessionClosesChannelAndThrows() {
        TextWebSocketFrame frame = new TextWebSocketFrame("hello");

        WebSocketException ex = assertThrows(WebSocketException.class, () -> channel.writeInbound(frame));

        assertEquals("websocket session does not exist", ex.getMessage());
        assertFalse(channel.isOpen(), "会话尚未建立时必须关闭管道");
        assertEquals(0, frame.refCnt());
        assertTrue(business.events.contains("close"), "关闭管道会触发连接关闭回调");
    }

    @Test
    void rejectedSubmissionReleasesFrameWithoutDispatch() {
        executor.accept = false;
        attachSession();
        TextWebSocketFrame frame = new TextWebSocketFrame("hello");

        channel.writeInbound(frame);

        assertTrue(business.events.isEmpty(), "未被受理的任务不得执行业务回调");
        assertEquals(1, executor.submissions);
        assertEquals(0, frame.refCnt(), "未受理路径同样要归还帧引用");
    }

    @Test
    void handlerFailurePropagatesAndReleasesFrame() {
        business.failOnText = true;
        attachSession();
        TextWebSocketFrame frame = new TextWebSocketFrame("hello");

        assertThrows(IllegalStateException.class, () -> channel.writeInbound(frame));

        assertEquals(0, frame.refCnt(), "业务回调抛异常后帧引用同样要归还");
    }

    @Test
    void channelInactiveNotifiesCloseClearsSessionAndPropagates() {
        attachSession();
        AtomicBoolean downstreamInactive = new AtomicBoolean();
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                downstreamInactive.set(true);
            }
        });

        channel.close();

        assertEquals(List.of("close"), business.events);
        assertSame(session, business.lastSession);
        assertNull(channel.attr(SESSION_KEY).get(), "关闭后会话附件必须摘除");
        assertTrue(downstreamInactive.get(), "连接关闭事件必须继续向后传递");
    }

    @Test
    void handshakeNoticeCreatesStandardSessionAndInvokesOpen() {
        HttpHeaders headers = new DefaultHttpHeaders().add("X-Request", "1");
        ChannelHandlerContext ctx = channel.pipeline().context(handler);

        handler.noticeFinishShakeHand(ctx,
                new WebSocketServerProtocolHandler.HandshakeComplete("/ws/chat", headers, null));

        assertEquals(List.of("open"), business.events);
        WebSocketSession opened = business.lastSession;
        assertInstanceOf(StandardWebSocketSession.class, opened);
        assertEquals("/ws/chat", opened.path());
        assertSame(headers, opened.headers());
        assertSame(opened, channel.attr(SESSION_KEY).get());

        TextWebSocketFrame frame = new TextWebSocketFrame("hello");
        channel.writeInbound(frame);

        assertEquals(List.of("open", "text"), business.events);
        assertSame(opened, business.lastSession, "后续帧必须使用握手时建立的会话");
        assertEquals("hello", business.payloads.get(0));
        assertEquals(0, frame.refCnt());
    }

    private void attachSession() {
        channel.attr(SESSION_KEY).set(session);
    }
}
