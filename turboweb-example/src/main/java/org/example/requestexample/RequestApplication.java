package org.example.requestexample;

import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class RequestApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(RequestApplication.class);
		server.controllers(new UserController(), new SimpleController());
		server.start();
	}
}
