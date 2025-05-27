package org.example.nodeshare;

import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;

public class UserApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(UserApplication.class);
		server.controllers(new UserController());

		Gateway gateway = new DefaultGateway();
		gateway.addServerNode("/order", "http://localhost:8081");
		server.gateway(gateway);

		server.start(8080);
	}
}
