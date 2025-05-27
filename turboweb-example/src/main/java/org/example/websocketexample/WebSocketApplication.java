package org.example.websocketexample;


import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class WebSocketApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(WebSocketApplication.class);
		server.websocket("/ws/(.*)", new OneWebSocketHandler());
		server.start();
	}
}
