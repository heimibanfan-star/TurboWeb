package top.heimi;

import org.turbo.web.core.http.middleware.ReactiveStaticResourceMiddleware;
import org.turbo.web.core.http.middleware.StaticResourceMiddleware;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.ReactiveUserController;
import top.heimi.controller.UserController;

/**
 * TODO
 */
public class ReactiveApplication {
	public static void main(String[] args) {
		TurboServer server = new DefaultTurboServer(ReactiveApplication.class);
		server.setIsReactiveServer(true);
		server.addMiddleware(new ReactiveStaticResourceMiddleware());
		server.addController(new ReactiveUserController());
		server.start();
	}
}
