package top.turboweb.core.initializer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboServerInitException;
import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.scheduler.VirtualThreadHttpScheduler;
import top.turboweb.core.initializer.HttpSchedulerInitializer;

/**
 * 默认的http调度器初始化器
 */
public class DefaultHttpSchedulerInitializer implements HttpSchedulerInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpSchedulerInitializer.class);

    @Override
    public HttpScheduler init(Processor processorChain, HttpServerConfig config) {
        HttpScheduler scheduler;
        if (!config.isEnableHttpSchedulerLimit()) {
            scheduler = new VirtualThreadHttpScheduler(
                    processorChain
            );
        } else {
            // 校验数据是否合法
            if (config.getHttpSchedulerLimitCount() < 1) {
                throw new TurboServerInitException("httpSchedulerLimitCount 必须大于 0");
            }
            if (config.getHttpSchedulerLimitTimeout() < 1) {
                throw new TurboServerInitException("httpSchedulerLimitTimeout 必须大于 0");
            }
            scheduler = new VirtualThreadHttpScheduler(
                    processorChain,
                    true,
                    config.getHttpSchedulerLimitCount(),
                    config.getHttpSchedulerLimitCacheThread(),
                    config.getHttpSchedulerLimitTimeout()
            );
            log.info(
                    "二级限流已开启: [并发线程数:{}, 缓存线程数:{}, 缓存时间:{}ms]",
                    config.getHttpSchedulerLimitCount(),
                    config.getHttpSchedulerLimitCacheThread(),
                    config.getHttpSchedulerLimitTimeout()
                    );
        }
        scheduler.setShowRequestLog(config.isShowRequestLog());
        log.info("http调度器初始化成功");
        return scheduler;
    }
}
