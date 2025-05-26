package org.example.routerexample;


import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class RouterApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(RouterApplication.class);
		server.controllers(new UserController());
		server.start();
	}
}
