package org.example.lifecycle;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class LifeCycleApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(LifeCycleApplication.class);
		server.listeners(new OneListener(), new TwoListener());
		server.http().middleware(new MyMiddleware());
		server.executeDefaultListener(false);
		server.start();
	}
}
