package org.turboweb.core.initializer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.config.ServerParamConfig;
import org.turboweb.core.http.handler.ExceptionHandlerMatcher;
import org.turboweb.core.http.middleware.Middleware;
import org.turboweb.core.http.scheduler.HttpScheduler;
import org.turboweb.core.http.scheduler.impl.VirtualThreadHttpScheduler;
import org.turboweb.core.http.scheduler.impl.ReactiveHttpScheduler;
import org.turboweb.core.http.session.SessionManagerProxy;
import org.turboweb.core.initializer.HttpSchedulerInitializer;

/**
 * 默认的http调度器初始化器
 */
public class DefaultHttpSchedulerInitializer implements HttpSchedulerInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpSchedulerInitializer.class);
    // 是否是反应式服务器
    private boolean isReactiveServer = false;

    @Override
    public void isReactive(boolean flag) {
        this.isReactiveServer = flag;
    }

    @Override
    public HttpScheduler init(SessionManagerProxy sessionManagerProxy, ExceptionHandlerMatcher matcher, Middleware chain, ServerParamConfig config) {
        HttpScheduler scheduler;
        // 判断是否采用反应式编程
        if (isReactiveServer) {
            scheduler = new ReactiveHttpScheduler(
                sessionManagerProxy,
                chain,
                matcher,
                config.getReactiveServiceThreadNum()
            );
        } else {
            scheduler = new VirtualThreadHttpScheduler(
                sessionManagerProxy,
                chain,
                matcher
            );
        }
        scheduler.setShowRequestLog(config.isShowRequestLog());
        log.info("http调度器初始化成功");
        return scheduler;
    }
}
