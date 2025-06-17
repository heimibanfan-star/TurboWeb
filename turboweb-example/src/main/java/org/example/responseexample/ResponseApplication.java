package org.example.responseexample;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class ResponseApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(ResponseApplication.class);
		server.http().controller(new UserController());
		server.start();
	}
}
