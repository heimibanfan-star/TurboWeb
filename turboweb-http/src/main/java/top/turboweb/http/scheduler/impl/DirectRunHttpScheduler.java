package top.turboweb.http.scheduler.impl;

import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.session.SessionManagerHolder;

/**
 * 直接采用IO线程运行
 */
public class DirectRunHttpScheduler extends SyncHttpScheduler {

    public DirectRunHttpScheduler(
            SessionManagerHolder sessionManagerHolder,
            Middleware chain,
            ExceptionHandlerMatcher exceptionHandlerMatcher
    ) {
        super(sessionManagerHolder, chain, exceptionHandlerMatcher, DirectRunHttpScheduler.class);
    }

    @Override
    protected void runTask(Runnable runnable) {
        runnable.run();
    }
}
