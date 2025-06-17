package org.example.sseexample;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class SseApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(SseApplication.class);
		server.http().controller(new HelloController());
		server.start();
	}
}
