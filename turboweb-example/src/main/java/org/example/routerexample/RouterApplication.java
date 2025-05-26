package org.example.routerexample;


import org.turboweb.core.server.StandardTurboWebServer;
import org.turboweb.core.server.TurboWebServer;

public class RouterApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(RouterApplication.class);
		server.controllers(new UserController());
		server.start();
	}
}
