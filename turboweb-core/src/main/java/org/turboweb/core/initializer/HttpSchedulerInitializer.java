package org.turboweb.core.initializer;

import org.turboweb.core.config.ServerParamConfig;
import org.turboweb.core.http.handler.ExceptionHandlerMatcher;
import org.turboweb.core.http.middleware.Middleware;
import org.turboweb.core.http.scheduler.HttpScheduler;
import org.turboweb.core.http.session.SessionManagerProxy;

/**
 * http调度器的初始化器
 */
public interface HttpSchedulerInitializer {

    /**
     * 设置是否为反应式调度器
     *
     * @param flag 表示
     */
    void isReactive(boolean flag);

    /**
     * 初始化http调度器
     *
     * @param sessionManagerProxy session管理器代理
     * @param matcher             异常处理匹配器
     * @param chain               中间件
     * @param config              服务器配置
     * @return http调度器
     */
    HttpScheduler init(
        SessionManagerProxy sessionManagerProxy,
        ExceptionHandlerMatcher matcher,
        Middleware chain,
        ServerParamConfig config
    );
}
