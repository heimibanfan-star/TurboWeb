package org.example.websocketexample;

import io.netty.buffer.ByteBuf;
import top.turboweb.websocket.AbstractWebSocketHandler;
import top.turboweb.websocket.WebSocketSession;

public class TwoWebSocketHandler extends AbstractWebSocketHandler {
	@Override
	public void onText(WebSocketSession session, String content) {
		System.out.println("收到文本消息: " + content);
	}

	@Override
	public void onBinary(WebSocketSession session, ByteBuf content) {
		content.retain();
		System.out.println("收到二进制消息: " + content);
	}
}
