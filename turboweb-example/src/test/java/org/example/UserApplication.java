package org.example;

import org.example.controller.UserController;
import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;

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
			.start( 8080);
	}
}
