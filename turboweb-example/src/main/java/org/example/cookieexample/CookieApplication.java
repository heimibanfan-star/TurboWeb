package org.example.cookieexample;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class CookieApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(CookieApplication.class);
		server.http().controller(new UserController());
		server.start();
	}
}
