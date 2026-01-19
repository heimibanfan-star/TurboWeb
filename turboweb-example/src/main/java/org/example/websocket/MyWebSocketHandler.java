package org.example.websocket;

import io.netty.buffer.ByteBuf;
import top.turboweb.websocket.handler.AbstractWebSocketHandler;
import top.turboweb.websocket.session.WebSocketSession;

public class MyWebSocketHandler extends AbstractWebSocketHandler {

    @Override
    public void onText(WebSocketSession session, String content) {
        System.out.println("收到文本消息: " + content);
        session.sendText("收到消息: " + content);
    }

    @Override
    public void onBinary(WebSocketSession session, ByteBuf content) {
        System.out.println("收到二进制消息");
        session.sendBinary(content);
    }

    @Override
    public void onOpen(WebSocketSession session) {
        String path = session.path();
        System.out.println("path: " + path);
    }

    @Override
    public void onClose(WebSocketSession session) {
        System.out.println("onClose");
    }

    @Override
    public void onPing(WebSocketSession session) {
        System.out.println("onPing");
    }

    @Override
    public void onPong(WebSocketSession session) {
        System.out.println("onPong");
    }
}
