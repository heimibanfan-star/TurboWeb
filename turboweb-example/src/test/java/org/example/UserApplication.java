package org.example;

import org.example.controller.UserController;
import org.turboweb.core.server.StandardTurboWebServer;
import org.turboweb.gateway.DefaultGateway;
import org.turboweb.gateway.Gateway;

/**
 * TODO
 */
public class UserApplication {
	public static void main(String[] args) {
		Gateway gateway = new DefaultGateway();
		gateway.addServerNode("/order", "http://localhost:8081");
		new StandardTurboWebServer(UserApplication.class)
			.controllers(new UserController())
			.gateway(gateway)
			.start(8080);
	}
}
