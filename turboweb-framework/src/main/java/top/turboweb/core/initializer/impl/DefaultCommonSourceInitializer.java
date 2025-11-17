package top.turboweb.core.initializer.impl;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.commons.utils.thread.DiskOpeThreadUtils;
import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.core.initializer.CommonSourceInitializer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 默认的通用资源初始化器
 */
public class DefaultCommonSourceInitializer implements CommonSourceInitializer {

    private final static List<Consumer<HttpServerConfig>> COMMON_SOURCE_INITIALIZERS = new ArrayList<>();
    private static final InternalLogger log = InternalLoggerFactory.getInstance(DefaultCommonSourceInitializer.class);

    static {
        COMMON_SOURCE_INITIALIZERS.add(
                // 用于初始化后背线程池
                config -> {
                    int backUpThreadCacheQueue = config.getDiskOpeThreadCacheQueue();
                    int backUpThreadCoreQueue = config.getDiskOpeThreadCoreQueue();
                    int backUpThreadMaxThreadNum = config.getDiskOpeThreadMaxThreadNum();
                    DiskOpeThreadUtils.init(backUpThreadCacheQueue, backUpThreadCoreQueue, backUpThreadMaxThreadNum);
                }
        );
    }

    @Override
    public void init(HttpServerConfig config) {
        COMMON_SOURCE_INITIALIZERS.forEach(initializer -> {
            initializer.accept(config);
        });
        log.info("Common source initializer success.");
    }
}
