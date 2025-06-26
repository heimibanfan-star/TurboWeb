package top.turboweb.http.scheduler.impl;

import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.session.SessionManagerHolder;

/**
 * 使用虚拟县城的阻塞线程调度器
 */
public class VirtualThreadHttpScheduler extends SyncHttpScheduler {

    public VirtualThreadHttpScheduler(
            Processor processorChain
    ) {
        super(
                processorChain,
                VirtualThreadHttpScheduler.class
        );
    }

    @Override
    protected void runTask(Runnable runnable) {
        // 使用虚拟线程允许任务
        Thread.ofVirtual().start(runnable);
    }
}
