package org.example.lifecycle;

import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class LifeCycleApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(LifeCycleApplication.class);
		server.listeners(new OneListener(), new TwoListener());
		server.middlewares(new MyMiddleware());
		server.executeDefaultListener(false);
		server.start();
	}
}
