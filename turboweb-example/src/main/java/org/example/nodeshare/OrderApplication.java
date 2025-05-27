package org.example.nodeshare;


import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;

public class OrderApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(OrderApplication.class);
		server.controllers(new OrderController());

		Gateway gateway = new DefaultGateway();
		gateway.addServerNode("/user", "http://localhost:8080");
		server.gateway(gateway);

		server.start(8081);
	}
}
