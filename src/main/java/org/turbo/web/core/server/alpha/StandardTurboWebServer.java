package org.turbo.web.core.server.alpha;

import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.gateway.Gateway;
import org.turbo.web.core.handler.piplines.HttpWorkerDispatcherHandler;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.session.SessionManager;
import org.turbo.web.core.http.ws.WebSocketHandler;
import org.turbo.web.core.initializer.impl.DefaultHttpClientInitializer;
import org.turbo.web.core.listener.TurboServerListener;

import java.util.function.Consumer;

/**
 * 标准的TurboWebServer
 */
public class StandardTurboWebServer extends CoreTurboWebServer implements TurboWebServer {

	private static final Logger log = LoggerFactory.getLogger(StandardTurboWebServer.class);
	private final ServerParamConfig config = new ServerParamConfig();
	private final HttpWorkDispatcherFactory httpWorkDispatcherFactory = new CoreHttpWorkDispatcherFactory();
	private final Class<?> mainClass;

	public StandardTurboWebServer(Class<?> mainClass) {
		this(mainClass, 0);
	}

	public StandardTurboWebServer(Class<?> mainClass, int ioThreadNum) {
		super(ioThreadNum);
		this.mainClass = mainClass;
	}

	@Override
	public void controllers(Object... controllers) {
		httpWorkDispatcherFactory.controllers(controllers);
	}

	@Override
	public void middlewares(Middleware... middlewares) {
		httpWorkDispatcherFactory.middlewares(middlewares);
	}

	@Override
	public void exceptionHandlers(Object... exceptionHandlers) {
		httpWorkDispatcherFactory.exceptionHandlers(exceptionHandlers);
	}

	@Override
	public void config(Consumer<ServerParamConfig> consumer) {
		consumer.accept(this.config);
	}

	@Override
	public void useReactiveServer() {
		httpWorkDispatcherFactory.useReactive();
	}

	@Override
	public void gateway(Gateway gateway) {
		httpWorkDispatcherFactory.gateway(gateway);
	}

	@Override
	public void websocket(String pathRegex, WebSocketHandler webSocketHandler) {
		httpWorkDispatcherFactory.websocketHandler(pathRegex, webSocketHandler);
	}

	@Override
	public void executeDefaultListener(boolean flag) {
		// TODO 待开发
	}

	@Override
	public void listeners(TurboServerListener... listeners) {
		// TODO 待开发
	}

	@Override
	public void replaceSessionManager(SessionManager sessionManager) {
		httpWorkDispatcherFactory.replaceSessionManager(sessionManager);
	}

	@Override
	public void start() {
		start(8080);
	}

	@Override
	public void start(int port) {
		start("0.0.0.0", port);
	}

	@Override
	public void start(String host, int port) {
		long start = System.currentTimeMillis();
		init();
		ChannelFuture channelFuture = startServer(host, port);
		channelFuture.addListener(future -> {
			long time = System.currentTimeMillis() - start;
			log.info("TurboWebServer start on: http://{}:{}, time: {} ms", host, port, time);
		});
	}

	private void init() {
		new DefaultHttpClientInitializer().init(workers());
		HttpWorkerDispatcherHandler httpWorkerDispatcherHandler = httpWorkDispatcherFactory.create(mainClass, config);
		initPipeline(httpWorkerDispatcherHandler, config.getMaxContentLength());
	}
}
