package top.turboweb.core.initializer.factory;

import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.router.RouterManager;
import top.turboweb.http.processor.CorsProcessor;
import top.turboweb.http.session.SessionManager;

import java.util.function.Consumer;

/**
 * http调度器的构造器接口
 */
public interface HttpSchedulerInitBuilder {


    /**
     * 添加异常处理器
     *
     * @param exceptionHandler 异常处理器
     */
    HttpSchedulerInitBuilder exceptionHandler(Object... exceptionHandler);

    /**
     * 替换session管理器
     *
     * @param sessionManager session管理器
     */
    HttpSchedulerInitBuilder replaceSessionManager(SessionManager sessionManager);

    /**
     * 添加中间件
     *
     * @param middleware 中间件
     */
    HttpSchedulerInitBuilder middleware(Middleware... middleware);

    /**
     * 设置路由管理器
     *
     * @param routerManager 路由管理器
     */
    HttpSchedulerInitBuilder routerManager(RouterManager routerManager);

    /**
     * 添加CORS处理器
     *
     * @param consumer CORS处理器配置的消费者
     */
    HttpSchedulerInitBuilder cors(Consumer<CorsProcessor.Config> consumer);

    /**
     * 添加JSON序列化器
     *
     * @param jsonSerializer JSON序列化器
     */
    HttpSchedulerInitBuilder jsonSerializer(JsonSerializer jsonSerializer);

    TurboWebServer and();
}
