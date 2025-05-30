package org.example.sessionexample;

import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.session.BackHoleSessionManager;

public class SessionApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(SessionApplication.class);
		server.controllers(new UserController());
		server.replaceSessionManager(new BackHoleSessionManager());
		server.start();
	}
}
