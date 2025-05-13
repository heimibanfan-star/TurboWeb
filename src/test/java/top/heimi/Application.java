package top.heimi;

import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.http.middleware.CorsMiddleware;
import org.turbo.web.core.http.middleware.sync.InterceptorMiddleware;
import org.turbo.web.core.server.StandardTurboWebServer;
import org.turbo.web.utils.log.TurboWebLogUtils;
import top.heimi.controllers.HelloController;
import top.heimi.handlers.GlobalExceptionHandler;
import top.heimi.interceptor.FirstInterceptor;
import top.heimi.interceptor.SecondInterceptor;
import top.heimi.listeners.FirstListener;
import top.heimi.listeners.SecondListener;
import top.heimi.middlewares.FirstMiddleware;
import top.heimi.middlewares.SecondMiddleware;

/**
 * TODO
 */
public class Application {
	private static final Logger log = LoggerFactory.getLogger(Application.class);

	public static void main(String[] args) {
		TurboWebLogUtils.simpleLog();
		ChannelFuture channelFuture = new StandardTurboWebServer(Application.class)
			.controllers(new HelloController())
			.start();
		channelFuture.addListener(future -> {
			log.info("启动成功");
		});
	}
}
