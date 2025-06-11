package top.turboweb.core.initializer;

import top.turboweb.core.config.ServerParamConfig;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.session.SessionManagerHolder;

/**
 * http调度器的初始化器
 */
public interface HttpSchedulerInitializer {

    /**
     * 初始化http调度器
     *
     * @param sessionManagerHolder session管理器代理
     * @param matcher             异常处理匹配器
     * @param chain               中间件
     * @param config              服务器配置
     * @return http调度器
     */
    HttpScheduler init(
        SessionManagerHolder sessionManagerHolder,
        ExceptionHandlerMatcher matcher,
        Middleware chain,
        ServerParamConfig config
    );

    /**
     * 禁用虚拟线程
     */
    void disableVirtualThread();
}
