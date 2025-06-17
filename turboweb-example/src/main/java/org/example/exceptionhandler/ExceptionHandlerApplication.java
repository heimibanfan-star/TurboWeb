package org.example.exceptionhandler;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class ExceptionHandlerApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(ExceptionHandlerApplication.class);
		server.http().controller(new UserController());
		server.http().exceptionHandler(new GlobalExceptionHandler());
		server.start();
	}
}
