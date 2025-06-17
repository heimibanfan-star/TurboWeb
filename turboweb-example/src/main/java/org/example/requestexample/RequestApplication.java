package org.example.requestexample;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class RequestApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(RequestApplication.class);
		server.http().controller(new UserController(), new SimpleController());
		server.start();
	}
}
