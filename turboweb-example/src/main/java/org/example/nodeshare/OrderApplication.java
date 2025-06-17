package org.example.nodeshare;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;

public class OrderApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(OrderApplication.class);
		server.http().controller(new OrderController());

		Gateway gateway = new DefaultGateway();
		gateway.addServerNode("/user", "http://localhost:8080");
		server.protocol().gateway(gateway);

		server.start(8081);
	}
}
