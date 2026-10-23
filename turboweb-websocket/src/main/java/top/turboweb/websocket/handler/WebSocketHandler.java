package top.turboweb.websocket.handler;

import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import top.turboweb.websocket.session.WebSocketSession;

/**
 * websocket消息处理器抽象类，把入站裸帧转换为可直接使用的消息内容。
 * <p>
 * 本类实现 {@link UnSafeWebSocketHandler} 的四个帧回调，在回调内完成内容拷贝后转交本类自身的
 * {@link #onText}、{@link #onBinary}、{@link #onPing}、{@link #onPong}：文本帧解码为文本，
 * 二进制帧、ping帧、pong帧的可读字节各拷贝为一份字节数组。帧对象不向子类暴露，拷贝出的内容
 * 与帧的生命周期无关，可跨回调留存；帧的引用计数由调用方管理，本类与子类均不释放帧。
 * </p>
 */
public abstract class WebSocketHandler implements UnSafeWebSocketHandler {

    @Override
    public void onOpen(WebSocketSession session) {}

    @Override
    public final void onUnSafeText(WebSocketSession session, TextWebSocketFrame textWebSocketFrame) {
        onText(session, textWebSocketFrame.text());
    }

    @Override
    public final void onUnSafeBinary(WebSocketSession session, BinaryWebSocketFrame binaryWebSocketFrame) {
        onBinary(session, ByteBufUtil.getBytes(binaryWebSocketFrame.content()));
    }

    @Override
    public final void onUnSafePing(WebSocketSession session, PingWebSocketFrame pingWebSocketFrame) {
        onPing(session, ByteBufUtil.getBytes(pingWebSocketFrame.content()));
    }

    @Override
    public final void onUnSafePong(WebSocketSession session, PongWebSocketFrame pongWebSocketFrame) {
        onPong(session, ByteBufUtil.getBytes(pongWebSocketFrame.content()));
    }

    @Override
    public void onClose(WebSocketSession session) {}

    /**
     * 处理收到文本消息
     *
     * @param session websocket session会话
     * @param content 文本消息内容
     */
    public abstract void onText(WebSocketSession session, String content);

    /**
     * 处理收到二进制消息
     *
     * @param session websocket session会话
     * @param content 二进制消息内容的拷贝
     */
    public abstract void onBinary(WebSocketSession session, byte[] content);

    /**
     * 处理收到ping消息，默认回送pong
     *
     * @param session websocket session会话
     * @param content ping帧内容的拷贝
     */
    public void onPing(WebSocketSession session, byte[] content) {
        session.sendPong();
    }

    /**
     * 处理收到pong消息，默认不做处理
     *
     * @param session websocket session会话
     * @param content pong帧内容的拷贝
     */
    public void onPong(WebSocketSession session, byte[] content) {
    }
}