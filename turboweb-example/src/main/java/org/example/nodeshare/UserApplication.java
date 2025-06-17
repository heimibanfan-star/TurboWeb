package org.example.nodeshare;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;

public class UserApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(UserApplication.class);
		server.http().controller(new UserController());

		Gateway gateway = new DefaultGateway();
		gateway.addServerNode("/order", "http://localhost:8081");
		server.protocol().gateway(gateway);

		server.start(8080);
	}
}
