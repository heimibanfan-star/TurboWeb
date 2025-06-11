package top.turboweb.core.server;

import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.core.config.ServerParamConfig;
import top.turboweb.gateway.Gateway;
import top.turboweb.core.dispatch.HttpProtocolDispatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.session.SessionManager;
import top.turboweb.websocket.WebSocketHandler;
import top.turboweb.core.initializer.impl.DefaultHttpClientInitializer;
import top.turboweb.core.listener.DefaultJacksonTurboWebListener;
import top.turboweb.core.listener.TurboWebListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 标准的TurboWebServer
 */
public class StandardTurboWebServer extends CoreTurboWebServer implements TurboWebServer {

	private static final Logger log = LoggerFactory.getLogger(StandardTurboWebServer.class);
	private final ServerParamConfig config = new ServerParamConfig();
	private final HttpWorkDispatcherFactory httpWorkDispatcherFactory = new CoreHttpWorkDispatcherFactory();
	private final Class<?> mainClass;
	private final List<TurboWebListener> defaultListeners = new ArrayList<>(1);
	private final List<TurboWebListener> customListeners = new ArrayList<>(1);
	private boolean executeDefaultListener = true;

	{
		defaultListeners.add(new DefaultJacksonTurboWebListener());
	}

	public StandardTurboWebServer(Class<?> mainClass) {
		this(mainClass, 0);
	}

	public StandardTurboWebServer(Class<?> mainClass, int ioThreadNum) {
		super(ioThreadNum);
		this.mainClass = mainClass;
	}

	@Override
	public TurboWebServer controllers(Object... controllers) {
		httpWorkDispatcherFactory.controllers(controllers);
		return this;
	}

	@Override
	public TurboWebServer controller(Object controller, Class<?> originClass) {
		httpWorkDispatcherFactory.controller(controller, originClass);
		return this;
	}

	@Override
	public TurboWebServer middlewares(Middleware... middlewares) {
		httpWorkDispatcherFactory.middlewares(middlewares);
		return this;
	}

	@Override
	public TurboWebServer exceptionHandlers(Object... exceptionHandlers) {
		httpWorkDispatcherFactory.exceptionHandlers(exceptionHandlers);
		return this;
	}

	@Override
	public TurboWebServer config(Consumer<ServerParamConfig> consumer) {
		consumer.accept(this.config);
		return this;
	}

	@Override
	public TurboWebServer gateway(Gateway gateway) {
		httpWorkDispatcherFactory.gateway(gateway);
		return this;
	}

	@Override
	public TurboWebServer websocket(String pathRegex, WebSocketHandler webSocketHandler) {
		httpWorkDispatcherFactory.websocketHandler(pathRegex, webSocketHandler);
		return this;
	}

	@Override
	public TurboWebServer websocket(String pathRegex, WebSocketHandler webSocketHandler, int forkJoinThreadNum) {
		httpWorkDispatcherFactory.websocketHandler(pathRegex, webSocketHandler, forkJoinThreadNum);
		return this;
	}

	@Override
	public TurboWebServer executeDefaultListener(boolean flag) {
		this.executeDefaultListener = flag;
		return this;
	}

	@Override
	public TurboWebServer listeners(TurboWebListener... listeners) {
		customListeners.addAll(List.of(listeners));
		return this;
	}

	@Override
	public TurboWebServer replaceSessionManager(SessionManager sessionManager) {
		httpWorkDispatcherFactory.replaceSessionManager(sessionManager);
		return this;
	}

	@Override
	public ChannelFuture start() {
		return start(8080);
	}

	@Override
	public ChannelFuture start(int port) {
		return start("0.0.0.0", port);
	}

	@Override
	public ChannelFuture start(String host, int port) {
		long start = System.currentTimeMillis();
		executeListenerBeforeInit();
		init();
		ChannelFuture channelFuture = startServer(host, port);
		channelFuture.addListener(future -> {
			if (future.isSuccess()) {
				executeListenerAfterServerStart();
				long time = System.currentTimeMillis() - start;
				log.info("TurboWebServer start on: http://{}:{}, time: {}ms", host, port, time);
			} else {
				log.error("TurboWebServer start failed: {}\n", future.cause().getMessage(), future.cause());
			}

		});
		return channelFuture;
	}

	/**
	 * 初始化
	 */
	private void init() {
		new DefaultHttpClientInitializer().init(workers());
		HttpProtocolDispatcher httpProtocolDispatcher = httpWorkDispatcherFactory.create(mainClass, config);
		initPipeline(httpProtocolDispatcher, config.getMaxContentLength());
	}

	/**
	 * 执行监听器
	 */
	private void executeListenerBeforeInit() {
		if (executeDefaultListener) {
			for (TurboWebListener turboWebListener : defaultListeners) {
				turboWebListener.beforeServerInit();
			}
		}
		for (TurboWebListener turboWebListener : customListeners) {
			turboWebListener.beforeServerInit();
		}
		log.info("TurboWeb初始化前置监听器方法执行完成");
	}

	/**
	 * 执行监听器
	 */
	private void executeListenerAfterServerStart() {
		if (executeDefaultListener) {
			for (TurboWebListener turboWebListener : defaultListeners) {
				turboWebListener.afterServerStart();
			}
		}
		for (TurboWebListener turboWebListener : customListeners) {
			turboWebListener.afterServerStart();
		}
		log.info("TurboWeb启动后监听器方法执行完成");
	}
}
