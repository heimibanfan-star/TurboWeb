package top.turboweb.core.initializer.factory;

import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.core.initializer.*;
import top.turboweb.core.initializer.impl.*;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.processor.CorsProcessor;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.session.SessionManager;
import top.turboweb.http.session.SessionManagerHolder;

import java.util.function.Consumer;

/**
 * HTTP调度器的初始化工厂
 */
public class HttpSchedulerInitFactory implements HttpSchedulerInitBuilder {

    private final TurboWebServer server;
    // 异常处理器的初始化器
    private final ExceptionHandlerInitializer exceptionHandlerInitializer;
    // HTTP调度器的初始化器
    private final HttpSchedulerInitializer httpSchedulerInitializer;
    // 会话管理器的代理初始化器
    private final SessionManagerProxyInitializer sessionManagerProxyInitializer;
    // 处理器的初始化器
    private final ProcessorInitializer processorInitializer;
    // 中间件的初始化器
    private final MiddlewareInitializer middlewareInitializer;

    {
        exceptionHandlerInitializer = new DefaultExceptionHandlerInitializer();
        httpSchedulerInitializer = new DefaultHttpSchedulerInitializer();
        sessionManagerProxyInitializer = new DefaultSessionManagerProxyInitializer();
        processorInitializer = new DefaultProcessorInitializer();
        middlewareInitializer = new DefaultMiddlewareInitializer();
    }

    public HttpSchedulerInitFactory(TurboWebServer server) {
        this.server = server;
    }

    /**
     * 禁用虚拟线程
     */
    @Override
    public HttpSchedulerInitBuilder disableVirtual() {
        httpSchedulerInitializer.disableVirtualThread();
        return this;
    }

    /**
     * 添加异常处理器
     *
     * @param exceptionHandler 异常处理器
     */
    @Override
    public HttpSchedulerInitBuilder exceptionHandler(Object... exceptionHandler) {
        exceptionHandlerInitializer.addExceptionHandler(exceptionHandler);
        return this;
    }

    /**
     * 替换session管理器
     *
     * @param sessionManager session管理器
     */
    @Override
    public HttpSchedulerInitBuilder replaceSessionManager(SessionManager sessionManager) {
        sessionManagerProxyInitializer.setSessionManager(sessionManager);
        return this;
    }

    /**
     * 添加中间件
     *
     * @param middleware 中间件
     */
    @Override
    public HttpSchedulerInitBuilder middleware(Middleware... middleware) {
        middlewareInitializer.addMiddleware(middleware);
        return this;
    }

    /**
     * 添加控制器
     *
     * @param controllers 控制器
     */
    @Override
    public HttpSchedulerInitBuilder controller(Object... controllers) {
        middlewareInitializer.addController(controllers);
        return this;
    }

    /**
     * 添加控制器
     *
     * @param instance 控制器的实例
     * @param originClass 控制器的原始类
     */
    @Override
    public HttpSchedulerInitBuilder controller(Object instance, Class<?> originClass) {
        middlewareInitializer.addController(instance, originClass);
        return this;
    }

    @Override
    public HttpSchedulerInitBuilder cors(Consumer<CorsProcessor.Config> consumer) {
        CorsProcessor.Config corsConfig = processorInitializer.getCorsConfig();
        consumer.accept(corsConfig);
        return this;
    }

    @Override
    public TurboWebServer and() {
        return server;
    }

    /**
     * 初始化http调度器
     *
     * @param mainClass 字节码对象
     * @param config 服务器配置
     * @return http调度器
     */
    public HttpScheduler createHttpScheduler(Class<?> mainClass, HttpServerConfig config) {
        // 初始化异常处理器匹配器
        ExceptionHandlerMatcher exceptionHandlerMatcher = exceptionHandlerInitializer.init();
        // 初始化session管理器
        SessionManagerHolder sessionManagerHolder = sessionManagerProxyInitializer.init(config);
        // 初始化中间件
        Middleware chain = middlewareInitializer.init(sessionManagerHolder, mainClass, exceptionHandlerMatcher, config);
        // 初始化内核处理器
        Processor processor = processorInitializer.init(chain, sessionManagerHolder, exceptionHandlerMatcher);
        // 初始化Http调度器
        return httpSchedulerInitializer.init(
                processor,
                config
        );
    }
}
