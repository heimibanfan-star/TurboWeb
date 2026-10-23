package top.turboweb.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link StandardWebSocketSession} 的帧写出、连接信息读取与关闭：用 EmbeddedChannel 承接写出的帧，
 * 逐帧核对类型与内容。
 */
class StandardWebSocketSessionTest {

    private EmbeddedChannel channel;

    private StandardWebSocketSession session;

    private HttpHeaders headers;

    @BeforeEach
    void setUp() {
        channel = new EmbeddedChannel();
        headers = new DefaultHttpHeaders().add("X-Request", "1");
        session = new StandardWebSocketSession(channel, "/ws/chat", headers);
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Test
    void sendTextWritesTextFrame() {
        ChannelFuture future = session.sendText("hello");

        assertNotNull(future);
        TextWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame);
        assertEquals("hello", frame.text());
        frame.release();
    }

    @Test
    void sendBinaryFromBytesWritesBinaryFrame() {
        byte[] payload = {1, 2, 3, 4};

        ChannelFuture future = session.sendBinary(payload);

        assertNotNull(future);
        BinaryWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame);
        assertArrayEquals(payload, ByteBufUtil.getBytes(frame.content()));
        frame.release();
    }

    @Test
    void sendBinaryFromByteBufKeepsSameBuffer() {
        ByteBuf buffer = Unpooled.copiedBuffer(new byte[]{5, 6});

        ChannelFuture future = session.sendBinary(buffer);

        assertNotNull(future);
        BinaryWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame);
        assertSame(buffer, frame.content());
        assertArrayEquals(new byte[]{5, 6}, ByteBufUtil.getBytes(frame.content()));
        frame.release();
    }

    @Test
    void sendWritesGivenFrameVerbatim() {
        TextWebSocketFrame custom = new TextWebSocketFrame("direct");

        ChannelFuture future = session.send(custom);

        assertNotNull(future);
        assertSame(custom, channel.readOutbound());
        custom.release();
    }

    @Test
    void sendPingWritesEmptyPingFrame() {
        session.sendPing();

        PingWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame);
        assertEquals(0, frame.content().readableBytes());
        frame.release();
    }

    @Test
    void sendPongWritesEmptyPongFrame() {
        session.sendPong();

        PongWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame);
        assertEquals(0, frame.content().readableBytes());
        frame.release();
    }

    @Test
    void closeClosesUnderlyingChannel() {
        session.close();

        assertFalse(channel.isOpen());
    }

    @Test
    void exposesHandshakePathAndHeaders() {
        assertEquals("/ws/chat", session.path());
        assertSame(headers, session.headers());
    }

    @Test
    void addressesDelegateToChannel() {
        assertSame(channel.remoteAddress(), session.remoteAddress());
        assertSame(channel.localAddress(), session.localAddress());
        assertNotNull(session.remoteAddress());
        assertNotNull(session.localAddress());
    }
}
