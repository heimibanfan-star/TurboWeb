package top.heimi;

import org.turbo.web.core.http.middleware.FreemarkerTemplateMiddleware;
import org.turbo.web.core.http.middleware.StaticResourceMiddleware;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.UserController;

/**
 * TODO
 */
public class Application {
	public static void main(String[] args) {
		TurboServer server = new DefaultTurboServer(Application.class);
		server.addMiddleware(new StaticResourceMiddleware(), new FreemarkerTemplateMiddleware());
		server.addController(new UserController());
		server.start();
	}
}
