package top.turboweb.core.server;

import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.core.config.ServerParamConfig;
import top.turboweb.core.initializer.factory.HttpProtocolDispatcherBuilder;
import top.turboweb.core.initializer.factory.HttpProtocolDispatcherInitFactory;
import top.turboweb.core.initializer.factory.HttpSchedulerInitBuilder;
import top.turboweb.core.initializer.factory.HttpSchedulerInitFactory;
import top.turboweb.gateway.Gateway;
import top.turboweb.core.dispatch.HttpProtocolDispatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.session.SessionManager;
import top.turboweb.websocket.WebSocketHandler;
import top.turboweb.core.initializer.impl.DefaultHttpClientInitializer;
import top.turboweb.core.listener.DefaultJacksonTurboWebListener;
import top.turboweb.core.listener.TurboWebListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * TurboWebServer实现类
 */
public class BootStrapTurboWebServer extends CoreTurboWebServer implements TurboWebServer {


	private static final Logger log = LoggerFactory.getLogger(BootStrapTurboWebServer.class);
	private final ServerParamConfig config = new ServerParamConfig();
	private final HttpSchedulerInitFactory httpSchedulerInitFactory;
	private final HttpProtocolDispatcherInitFactory httpProtocolDispatcherInitFactory;
	private final Class<?> mainClass;
	private final List<TurboWebListener> defaultListeners = new ArrayList<>(1);
	private final List<TurboWebListener> customListeners = new ArrayList<>(1);
	private boolean executeDefaultListener = true;

	{
		httpSchedulerInitFactory = new HttpSchedulerInitFactory(this);
		httpProtocolDispatcherInitFactory = new HttpProtocolDispatcherInitFactory(this);
		defaultListeners.add(new DefaultJacksonTurboWebListener());
	}

	public BootStrapTurboWebServer(Class<?> mainClass) {
		this(mainClass, 0);
	}

	public BootStrapTurboWebServer(Class<?> mainClass, int ioThreadNum) {
		super(ioThreadNum);
		this.mainClass = mainClass;
	}

	@Override
	public HttpProtocolDispatcherBuilder protocol() {
		return this.httpProtocolDispatcherInitFactory;
	}

	@Override
	public HttpSchedulerInitBuilder http() {
		return this.httpSchedulerInitFactory;
	}

	@Override
	public TurboWebServer config(Consumer<ServerParamConfig> consumer) {
		consumer.accept(this.config);
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
		// 创建http调度器
		HttpScheduler httpScheduler = httpSchedulerInitFactory.createHttpScheduler(mainClass, config);
		// 创建http协议分发器
		HttpProtocolDispatcher httpProtocolDispatcher = httpProtocolDispatcherInitFactory.createDispatcher(httpScheduler);
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

	/**
	 * 创建TurboWebServer
	 *
	 * @param mainClass 主类
	 * @return TurboWebServer
	 */
	public static TurboWebServer create(Class<?> mainClass) {
		return new BootStrapTurboWebServer(mainClass);
	}

	/**
	 * 创建TurboWebServer
	 *
	 * @param mainClass 主类
	 * @param ioThreadNum IO线程数
	 * @return TurboWebServer
	 */
	public static TurboWebServer create(Class<?> mainClass, int ioThreadNum) {
		return new BootStrapTurboWebServer(mainClass, ioThreadNum);
	}
}
