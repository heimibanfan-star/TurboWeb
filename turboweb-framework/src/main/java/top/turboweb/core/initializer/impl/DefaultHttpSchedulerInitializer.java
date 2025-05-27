package top.turboweb.core.initializer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.core.config.ServerParamConfig;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.scheduler.impl.VirtualThreadHttpScheduler;
import top.turboweb.http.scheduler.impl.ReactiveHttpScheduler;
import top.turboweb.http.session.SessionManagerProxy;
import top.turboweb.core.initializer.HttpSchedulerInitializer;

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
