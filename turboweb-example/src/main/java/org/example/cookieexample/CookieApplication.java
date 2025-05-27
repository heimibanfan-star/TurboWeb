package org.example.cookieexample;

import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class CookieApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(CookieApplication.class);
		server.controllers(new UserController());
		server.start();
	}
}
