package org.example;

import org.example.controller.HelloController;
import org.turboweb.core.server.StandardTurboWebServer;

/**
 * TODO
 */
public class Application {
	public static void main(String[] args) {
		new StandardTurboWebServer(Application.class)
			.controllers(new HelloController())
			.start();
	}
}
