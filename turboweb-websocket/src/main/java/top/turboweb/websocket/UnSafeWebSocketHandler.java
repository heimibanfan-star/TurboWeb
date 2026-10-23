package top.turboweb.websocket;

import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * websocket裸帧处理器，按帧类型分别提供回调。
 * <p>
 * 回调入参是管道收到的 Netty 原始帧对象，而非解析后的消息内容：文本帧的文本、二进制帧的
 * 字节内容均由实现方自帧上自行读取。帧的引用计数由调用方管理，回调返回后不再持有该帧，
 * 实现方不得留存帧对象，也不得在回调中释放该帧。
 * </p>
 */
public interface UnSafeWebSocketHandler {

    /**
     * 链接建立时调度
     *
     * @param session websocket session会话
     */
    void onOpen(WebSocketSession session);

    /**
     * 处理收到文本帧
     *
     * @param session websocket session会话
     * @param textWebSocketFrame Netty原始文本帧
     */
    void onUnSafeText(WebSocketSession session, TextWebSocketFrame textWebSocketFrame);

    /**
     * 处理收到二进制帧
     *
     * @param session websocket session会话
     * @param binaryWebSocketFrame Netty原始二进制帧
     */
    void onUnSafeBinary(WebSocketSession session, BinaryWebSocketFrame binaryWebSocketFrame);

    /**
     * 处理收到ping帧
     *
     * @param session websocket session会话
     * @param pingWebSocketFrame Netty原始ping帧
     */
    void onUnSafePing(WebSocketSession session, PingWebSocketFrame pingWebSocketFrame);

    /**
     * 处理收到pong帧
     *
     * @param session websocket session会话
     * @param pongWebSocketFrame Netty原始pong帧
     */
    void onUnSafePong(WebSocketSession session, PongWebSocketFrame pongWebSocketFrame);

    /**
     * 处理收到关闭消息
     *
     * @param session websocket session会话
     */
    void onClose(WebSocketSession session);
}