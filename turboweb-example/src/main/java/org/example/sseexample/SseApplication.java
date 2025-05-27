package org.example.sseexample;


import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class SseApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(SseApplication.class);
		server.controllers(new HelloController());
		server.start();
	}
}
