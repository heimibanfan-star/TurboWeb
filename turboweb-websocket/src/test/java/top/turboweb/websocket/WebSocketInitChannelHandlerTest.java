package top.turboweb.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.concurrent.thread.VirtualThreadMicroTaskExecutor;
import top.turboweb.server.runtime.ChannelAttributeKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WebSocketInitChannelHandler} 的建造校验、升级请求识别与协议装配：
 * 升级请求完成握手后追加协议处理器与分发处理器，其余请求原样向后转发。
 */
class WebSocketInitChannelHandlerTest {

    private final RecordingWebSocketHandler business = new RecordingWebSocketHandler();

    private final DirectMicroTaskExecutor executor = new DirectMicroTaskExecutor();

    @Test
    void builderRejectsNullOrBlankPath() {
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> WebSocketInitChannelHandler.builder().websocketPath("   "));
        assertEquals("websocketPath can not be blank", blank.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> WebSocketInitChannelHandler.builder().websocketPath(null));
        assertThrows(IllegalArgumentException.class,
                () -> WebSocketInitChannelHandler.builder().websocketPath(""));
    }

    @Test
    void builderRejectsNullHandler() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> WebSocketInitChannelHandler.builder().webSocketHandler(null));

        assertEquals("webSocketHandler can not be null", ex.getMessage());
    }

    @Test
    void builderRejectsNullExecutor() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> WebSocketInitChannelHandler.builder().microTaskExecutor(null));

        assertEquals("microTaskExecutor must not be null", ex.getMessage());
    }

    @Test
    void buildRequiresPath() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> WebSocketInitChannelHandler.builder().webSocketHandler(business).build());

        assertEquals("websocketPath can not be blank", ex.getMessage());
    }

    @Test
    void buildRequiresHandler() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> WebSocketInitChannelHandler.builder().websocketPath("/ws/.*").build());

        assertEquals("webSocketHandler can not be null", ex.getMessage());
    }

    @Test
    void buildKeepsInjectedExecutor() throws Exception {
        WebSocketInitChannelHandler handler = newHandler();

        assertSame(executor, TestSupport.executorOf(handler));
    }

    @Test
    void buildCreatesUnlimitedVirtualThreadExecutorByDefault() throws Exception {
        WebSocketInitChannelHandler handler = WebSocketInitChannelHandler.builder()
                .websocketPath("/ws/.*")
                .webSocketHandler(business)
                .build();

        MicroTaskExecutor created = TestSupport.executorOf(handler);
        assertInstanceOf(VirtualThreadMicroTaskExecutor.class, created);
        assertFalse(((VirtualThreadMicroTaskExecutor) created).isLimited(),
                "缺省执行器必须是非受限虚拟线程实现");
    }

    @Test
    void matchingUpgradeCompletesHandshakeAndInstallsDispatcher() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerCodec(), newHandler());

        channel.writeInbound(handshakeRequest("/ws/chat", "keep-alive, UPGRADE"));

        assertEquals(1, handlerCount(channel.pipeline(), WebSocketServerProtocolHandler.class));
        assertEquals(1, handlerCount(channel.pipeline(), WebSocketChannelHandler.class));

        ByteBuf response = channel.readOutbound();
        assertNotNull(response);
        String responseText = response.toString(CharsetUtil.US_ASCII);
        response.release();
        assertTrue(responseText.startsWith("HTTP/1.1 101"), responseText);

        assertEquals(List.of("open"), business.events);
        WebSocketSession session = business.lastSession;
        assertEquals("/ws/chat", session.path());
        assertEquals("WebSocket", session.headers().get(HttpHeaderNames.UPGRADE));

        TextWebSocketFrame frame = new TextWebSocketFrame("hello");
        channel.writeInbound(frame);

        assertEquals(List.of("open", "text"), business.events);
        assertEquals("hello", business.payloads.get(0));
        assertSame(session, business.lastSession);
        assertEquals(0, frame.refCnt());

        channel.finishAndReleaseAll();
    }

    @Test
    void handshakeCompleteMarksChannelIdleExempt() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerCodec(), newHandler());

        channel.writeInbound(handshakeRequest("/ws/chat", "Upgrade"));

        assertNotNull(channel.attr(ChannelAttributeKeys.IDLE_EXEMPT).get(),
                "握手完成移交长生命周期协议的连接必须落空闲豁免标记");
        channel.finishAndReleaseAll();
    }

    @Test
    void nonUpgradedRequestDoesNotMarkIdleExempt() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerCodec(), newHandler());

        channel.writeInbound(upgradeRequest(HttpMethod.GET, "/elsewhere", "websocket", "Upgrade"));

        assertNull(channel.attr(ChannelAttributeKeys.IDLE_EXEMPT).get(),
                "未完成协议移交的连接不落空闲豁免标记");
        channel.finishAndReleaseAll();
    }

    @Test
    void nonHandshakeUserEventIsForwardedWithoutOpeningSession() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerCodec(), newHandler());
        channel.writeInbound(handshakeRequest("/ws/chat", "Upgrade"));
        List<Object> events = new ArrayList<>();
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                events.add(evt);
                ctx.fireUserEventTriggered(evt);
            }
        });

        channel.pipeline().fireUserEventTriggered("custom-event");

        assertTrue(events.contains("custom-event"), "非握手完成事件必须继续向后传递");
        assertEquals(List.of("open"), business.events, "非握手完成事件不得再次触发 onOpen");

        channel.finishAndReleaseAll();
    }

    @Test
    void nonUpgradeRequestsAreForwardedWithoutInstallingHandlers() {
        assertForwardedOnly(upgradeRequest(HttpMethod.POST, "/ws/chat", "websocket", "Upgrade"));
        assertForwardedOnly(upgradeRequest(HttpMethod.GET, "/ws/chat", "h2c", "Upgrade"));
        assertForwardedOnly(upgradeRequest(HttpMethod.GET, "/ws/chat", "websocket", null));
        assertForwardedOnly(upgradeRequest(HttpMethod.GET, "/ws/chat", "websocket", "keep-alive"));
        assertForwardedOnly(upgradeRequest(HttpMethod.GET, "/elsewhere", "websocket", "Upgrade"));
    }

    @Test
    void existingProtocolHandlerIsNotDuplicatedAndNoDispatcherIsInstalled() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerCodec(), newHandler());
        channel.pipeline().addLast(new WebSocketServerProtocolHandler("/ws/chat"));

        channel.writeInbound(handshakeRequest("/ws/chat", "Upgrade"));

        assertEquals(1, handlerCount(channel.pipeline(), WebSocketServerProtocolHandler.class));
        assertEquals(0, handlerCount(channel.pipeline(), WebSocketChannelHandler.class));
        assertTrue(business.events.isEmpty(), "已存在协议处理器时不再追加分发处理器");

        ByteBuf response = channel.readOutbound();
        assertNotNull(response);
        String responseText = response.toString(CharsetUtil.US_ASCII);
        response.release();
        assertTrue(responseText.startsWith("HTTP/1.1 101"), responseText);

        channel.finishAndReleaseAll();
    }

    private WebSocketInitChannelHandler newHandler() {
        return WebSocketInitChannelHandler.builder()
                .websocketPath("/ws/.*")
                .webSocketHandler(business)
                .microTaskExecutor(executor)
                .build();
    }

    /**
     * 断言请求未被当作 websocket 升级：不追加协议处理器与分发处理器，
     * 请求以同一实例继续向后传递（引用由下游归还）。
     */
    private void assertForwardedOnly(FullHttpRequest request) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerCodec(), newHandler());
        AtomicReference<Object> forwarded = new AtomicReference<>();
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                forwarded.set(msg);
                ReferenceCountUtil.release(msg);
            }
        });

        channel.writeInbound(request);

        assertEquals(0, handlerCount(channel.pipeline(), WebSocketServerProtocolHandler.class));
        assertEquals(0, handlerCount(channel.pipeline(), WebSocketChannelHandler.class));
        assertSame(request, forwarded.get(), "非升级请求必须原样向后转发");
        assertTrue(business.events.isEmpty());

        channel.finishAndReleaseAll();
    }

    private static FullHttpRequest handshakeRequest(String uri, String connection) {
        FullHttpRequest request = upgradeRequest(HttpMethod.GET, uri, "WebSocket", connection);
        request.headers()
                .set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==")
                .set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "13");
        return request;
    }

    private static FullHttpRequest upgradeRequest(HttpMethod method, String uri, String upgrade, String connection) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
        if (upgrade != null) {
            request.headers().set(HttpHeaderNames.UPGRADE, upgrade);
        }
        if (connection != null) {
            request.headers().set(HttpHeaderNames.CONNECTION, connection);
        }
        return request;
    }

    private static long handlerCount(ChannelPipeline pipeline, Class<?> type) {
        return pipeline.toMap().values().stream().filter(type::isInstance).count();
    }
}
