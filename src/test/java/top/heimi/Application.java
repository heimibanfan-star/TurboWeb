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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TODO
 */
public class Application {
	private static final Logger log = LoggerFactory.getLogger(Application.class);

	public static void main(String[] args) throws IOException {
		TurboWebLogUtils.simpleLog();
		ChannelFuture channelFuture = new StandardTurboWebServer(Application.class, 16)
			.controllers(new HelloController())
			.config(config -> {
				config.setShowRequestLog(false);
			})
			.start();
		channelFuture.addListener(future -> {
			log.info("启动成功");
		});
	}
}
