package top.heimi;

import org.turbo.web.core.http.middleware.FreemarkerTemplateMiddleware;
import org.turbo.web.core.http.middleware.StaticResourceMiddleware;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.alpha.StandardTurboWebServer;
import org.turbo.web.core.server.alpha.TurboWebServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.UserController;

/**
 * TODO
 */
public class Application {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(Application.class);
		server.controllers(new UserController());
		server.start("127.0.0.1", 8080);
	}
}
