package org.example.fileexample;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

public class FileApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(FileApplication.class);
		server.http().controller(new FileController());
		server.start();
	}
}
