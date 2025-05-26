package org.example.exceptionhandler;


import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class ExceptionHandlerApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(ExceptionHandlerApplication.class);
		server.controllers(new UserController());
		server.exceptionHandlers(new GlobalExceptionHandler());
		server.start();
	}
}
