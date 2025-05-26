package org.example;

import io.netty.buffer.ByteBuf;
import org.example.controller.HelloController;
import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.websocket.AbstractWebSocketHandler;
import top.turboweb.websocket.WebSocketSession;

import java.util.concurrent.ExecutionException;

/**
 * TODO
 */
public class Application {
	public static void main(String[] args) throws ExecutionException, InterruptedException {
		new StandardTurboWebServer(Application.class)
			.controllers(new HelloController())
			.websocket("/ws", new AbstractWebSocketHandler() {
				@Override
				public void onText(WebSocketSession session, String content) {
					session.sendText("收到消息: " + content);
					System.out.println("收到文本消息: " + content);
				}

				@Override
				public void onBinary(WebSocketSession session, ByteBuf content) {

				}
			})
			.start();
	}
}
