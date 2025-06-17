package org.example.websocketexample;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class WebSocketApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(WebSocketApplication.class);
		server.protocol().websocket("/ws/(.*)", new OneWebSocketHandler());
		server.start();
	}
}
