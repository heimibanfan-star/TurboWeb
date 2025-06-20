package org.example.paramexample;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

import java.nio.charset.StandardCharsets;

public class ParamApplication {
	public static void main(String[] args) {
		TurboWebServer server = new BootStrapTurboWebServer(ParamApplication.class, 1);
		server.configServer(config -> {
			// 由此进行参数配置
			config.setCharset(StandardCharsets.UTF_8);
			config.setMaxContentLength(1024 * 1024 * 10);
			config.setShowRequestLog(true);
			config.setSessionCheckTime(300000);
			config.setSessionMaxNotUseTime(-1);
			config.setSessionCheckThreshold(256);
		});
	}
}
