package top.turboweb.core.initializer;

import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.scheduler.HttpScheduler;

/**
 * http调度器的初始化器
 */
public interface HttpSchedulerInitializer {

    /**
     * 初始化http调度器
     *
     * @param processorChain 内核处理器链
     * @param config               服务器配置
     * @return http调度器
     */
    HttpScheduler init(
            Processor processorChain,
            HttpServerConfig config
    );
}
