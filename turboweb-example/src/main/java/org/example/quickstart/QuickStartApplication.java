package org.example.quickstart;

import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

/**
 * 快速开始
 */
public class QuickStartApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(QuickStartApplication.class);
		server.controllers(new HelloController());
		server.start();
	}

	public static void example() {
//		TurboWebServer server = new StandardTurboWebServer(QuickStartApplication.class);
		// 第二个参数是IO线程的数量，默认是单线程
		TurboWebServer server = new StandardTurboWebServer(QuickStartApplication.class, 1);
		server.controllers(new HelloController());
//		server.start();
//		server.start(8080);
		server.start("0.0.0.0", 8080);
	}
}
