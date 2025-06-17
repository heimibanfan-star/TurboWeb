package org.example.sessionexample;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.session.BackHoleSessionManager;

public class SessionApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(SessionApplication.class);
		server.http().controller(new UserController());
		server.http().replaceSessionManager(new BackHoleSessionManager());
		server.start();
	}
}
