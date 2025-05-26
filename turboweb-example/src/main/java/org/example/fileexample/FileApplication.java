package org.example.fileexample;

import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class FileApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(FileApplication.class);
		server.controllers(new FileController());
		server.start();
	}
}
