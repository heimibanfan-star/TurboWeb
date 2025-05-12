package top.heimi;

import org.turbo.web.core.server.StandardTurboWebServer;
import org.turbo.web.core.server.TurboWebServer;
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
	public static void main(String[] args) {
		TurboWebLogUtils.simpleLog();
		TurboWebServer server = new StandardTurboWebServer(Application.class);
		server.controllers(new HelloController());
		server.exceptionHandlers(new GlobalExceptionHandler());
		server.middlewares(new FirstMiddleware(), new SecondMiddleware());
		server.listeners(new FirstListener(), new SecondListener());
		server.start();
	}
}
