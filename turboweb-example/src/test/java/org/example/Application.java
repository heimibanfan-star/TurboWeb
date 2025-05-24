package org.example;

import io.netty.buffer.ByteBuf;
import io.netty.channel.nio.NioEventLoopGroup;
import org.example.controller.HelloController;
import org.turboweb.client.HttpClientUtils;
import org.turboweb.client.PromiseHttpClient;
import org.turboweb.client.config.HttpClientConfig;
import org.turboweb.client.result.RestResponseResult;
import org.turboweb.core.server.StandardTurboWebServer;
import org.turboweb.websocket.AbstractWebSocketHandler;
import org.turboweb.websocket.WebSocketSession;

import java.util.Map;
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
