package org.example.sessionexample;

import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class SessionApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(SessionApplication.class);
		server.controllers(new UserController());
		server.start();
	}
}
