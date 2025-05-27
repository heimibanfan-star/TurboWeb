package org.example.websocketexample;

import io.netty.handler.codec.http.websocketx.*;
import top.turboweb.websocket.WebSocketHandler;
import top.turboweb.websocket.WebSocketSession;


public class OneWebSocketHandler implements WebSocketHandler {
	@Override
	public void onOpen(WebSocketSession session) {
		String websocketPath = session.getWebSocketConnectInfo().getWebsocketPath();
		System.out.println("onOpen: " + websocketPath);
	}

	@Override
	public void onMessage(WebSocketSession session, WebSocketFrame webSocketFrame) {
		try {
			if (webSocketFrame instanceof TextWebSocketFrame textWebSocketFrame) {
				System.out.println("onMessage: " + textWebSocketFrame.text());
				session.sendText(textWebSocketFrame.text());
			} else if (webSocketFrame instanceof BinaryWebSocketFrame binaryWebSocketFrame) {
				System.out.println("onMessage: " + binaryWebSocketFrame.content());
			} else if (webSocketFrame instanceof PingWebSocketFrame pingWebSocketFrame) {
				System.out.println("onMessage: " + pingWebSocketFrame.content());
			} else if (webSocketFrame instanceof PongWebSocketFrame pongWebSocketFrame) {
				System.out.println("onMessage: " + pongWebSocketFrame.content());
			}
		} finally {
			webSocketFrame.release();
		}

	}

	@Override
	public void onClose(WebSocketSession session) {
		System.out.println("onClose");
	}
}
