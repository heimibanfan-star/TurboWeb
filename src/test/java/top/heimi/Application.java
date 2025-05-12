package top.heimi;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.http.middleware.CorsMiddleware;
import org.turbo.web.core.http.ws.AbstractWebSocketHandler;
import org.turbo.web.core.http.ws.WebSocketSession;
import org.turbo.web.core.server.StandardTurboWebServer;
import org.turbo.web.core.server.TurboWebServer;
import org.turbo.web.core.server.legacy.DefaultTurboServer;
import org.turbo.web.core.server.legacy.TurboServer;
import org.turbo.web.utils.log.TurboWebLogUtils;
import top.heimi.controllers.HelloController;
import top.heimi.handlers.GlobalExceptionHandler;
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
			.exceptionHandlers(new GlobalExceptionHandler())
			.middlewares(new FirstMiddleware(), new SecondMiddleware())
			.middlewares(new CorsMiddleware())
			.listeners(new FirstListener(), new SecondListener())
			.websocket("/ws/(.*)", new AbstractWebSocketHandler() {

				@Override
				public void onOpen(WebSocketSession session) {
					System.out.println("websocket连接成功");
				}

				@Override
				public void onText(WebSocketSession session, String content) {
					System.out.println("收到文本消息: " + content);
				}

				@Override
				public void onBinary(WebSocketSession session, ByteBuf content) {

				}
			}, 2)
			.start();
		channelFuture.addListener(future -> {
			log.info("启动成功");
		});
	}
}
