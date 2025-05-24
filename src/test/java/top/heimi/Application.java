package top.heimi;

import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.http.middleware.ServerInfoMiddleware;
import org.turboweb.core.server.StandardTurboWebServer;
import org.turboweb.utils.log.TurboWebLogUtils;
import top.heimi.controllers.HelloController;

import java.io.IOException;

/**
 * TODO
 */
public class Application {
	private static final Logger log = LoggerFactory.getLogger(Application.class);

	public static void main(String[] args) throws IOException {
		TurboWebLogUtils.simpleLog();
		ChannelFuture channelFuture = new StandardTurboWebServer(Application.class, 1)
			.controllers(new HelloController())
			.config(config -> {
//				config.setShowRequestLog(false);
			})
			.middlewares(new ServerInfoMiddleware())
			.start();
		channelFuture.addListener(future -> {
			log.info("启动成功");
		});
	}
}
