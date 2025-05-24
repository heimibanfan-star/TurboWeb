package org.example;

import org.example.controller.OrderController;
import org.turboweb.core.server.StandardTurboWebServer;
import org.turboweb.gateway.DefaultGateway;
import org.turboweb.gateway.Gateway;

/**
 * TODO
 */
public class OrderApplication {
	public static void main(String[] args) {
		Gateway gateway = new DefaultGateway();
		gateway.addServerNode("/user", "http://localhost:8080");
		new StandardTurboWebServer(OrderApplication.class)
			.controllers(new OrderController())
			.start(8081);
	}
}
