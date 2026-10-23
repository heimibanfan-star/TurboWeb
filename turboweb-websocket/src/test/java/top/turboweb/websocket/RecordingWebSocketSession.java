package top.turboweb.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 记录全部发送与关闭调用的会话替身。
 */
final class RecordingWebSocketSession implements WebSocketSession {

    /** 按调用顺序记录的方法名 */
    final List<String> calls = new ArrayList<>();

    /** 按调用顺序记录的发送载荷 */
    final List<Object> sent = new ArrayList<>();

    private final String path;

    private final HttpHeaders headers;

    RecordingWebSocketSession() {
        this("/ws/chat", new DefaultHttpHeaders());
    }

    RecordingWebSocketSession(String path, HttpHeaders headers) {
        this.path = path;
        this.headers = headers;
    }

    @Override
    public ChannelFuture sendText(String message) {
        calls.add("sendText");
        sent.add(message);
        return null;
    }

    @Override
    public ChannelFuture sendBinary(byte[] message) {
        calls.add("sendBinary(byte[])");
        sent.add(message);
        return null;
    }

    @Override
    public ChannelFuture sendBinary(ByteBuf byteBuf) {
        calls.add("sendBinary(ByteBuf)");
        sent.add(byteBuf);
        return null;
    }

    @Override
    public ChannelFuture send(WebSocketFrame webSocketFrame) {
        calls.add("send");
        sent.add(webSocketFrame);
        return null;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public void sendPing() {
        calls.add("sendPing");
    }

    @Override
    public void sendPong() {
        calls.add("sendPong");
    }

    @Override
    public void close() {
        calls.add("close");
    }

    @Override
    public SocketAddress remoteAddress() {
        return new InetSocketAddress("127.0.0.1", 8080);
    }

    @Override
    public SocketAddress localAddress() {
        return new InetSocketAddress("127.0.0.1", 9090);
    }
}
