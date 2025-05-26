package org.example.responseexample;


import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class ResponseApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(ResponseApplication.class);
		server.controllers(new UserController());
		server.start();
	}
}
