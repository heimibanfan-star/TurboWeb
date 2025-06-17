package top.turboweb.core.initializer.factory;

import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.session.SessionManager;

/**
 * http调度器的构造器接口
 */
public interface HttpSchedulerInitBuilder {

    /**
     * 禁用虚拟线程
     */
    HttpSchedulerInitBuilder disableVirtual();

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
     * 添加控制器
     *
     * @param controllers 控制器
     */
    HttpSchedulerInitBuilder controller(Object... controllers);

    /**
     * 添加控制器
     *
     * @param instance 控制器的实例
     * @param originClass 控制器的原始类
     */
    HttpSchedulerInitBuilder controller(Object instance, Class<?> originClass);

    TurboWebServer and();
}
