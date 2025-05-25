package org.example.requestexample;

import org.turboweb.core.server.StandardTurboWebServer;
import org.turboweb.core.server.TurboWebServer;

public class RequestApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(RequestApplication.class);
		server.controllers(new UserController());
		server.start();
	}
}
