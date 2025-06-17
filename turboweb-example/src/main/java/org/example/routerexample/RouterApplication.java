package org.example.routerexample;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class RouterApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(RouterApplication.class);
		server.http().controller(new UserController());
		server.start();
	}
}
