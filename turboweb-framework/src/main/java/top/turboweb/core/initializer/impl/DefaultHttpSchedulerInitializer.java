package top.turboweb.core.initializer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.scheduler.impl.DirectRunHttpScheduler;
import top.turboweb.http.scheduler.impl.VirtualThreadHttpScheduler;
import top.turboweb.http.session.SessionManagerHolder;
import top.turboweb.core.initializer.HttpSchedulerInitializer;

/**
 * 默认的http调度器初始化器
 */
public class DefaultHttpSchedulerInitializer implements HttpSchedulerInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpSchedulerInitializer.class);
    private boolean useVirtualThread = true;

    @Override
    public HttpScheduler init(SessionManagerHolder sessionManagerHolder, ExceptionHandlerMatcher matcher, Middleware chain, HttpServerConfig config) {
        HttpScheduler scheduler;
        if (useVirtualThread) {
            scheduler = new VirtualThreadHttpScheduler(
                    sessionManagerHolder,
                    chain,
                    matcher
            );
            log.info("Use virtualThreadHttpScheduler");
        } else {
            scheduler = new DirectRunHttpScheduler(
                    sessionManagerHolder,
                    chain,
                    matcher
            );
            log.warn("Current Use directRunHttpScheduler Please Increase IO Thread Num");
        }
        scheduler.setShowRequestLog(config.isShowRequestLog());
        log.info("http调度器初始化成功");
        return scheduler;
    }

    @Override
    public void disableVirtualThread() {
        useVirtualThread = false;
    }
}
