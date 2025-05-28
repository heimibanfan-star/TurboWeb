package org.example;

import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.middleware.sync.StaticResourceMiddleware;

/**
 * TODO
 */
public class FileApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(FileApplication.class);
		StaticResourceMiddleware resourceMiddleware = new StaticResourceMiddleware();
		resourceMiddleware.setStaticResourceUri("/");
		server.middlewares(resourceMiddleware);
		server.start();
	}
}
