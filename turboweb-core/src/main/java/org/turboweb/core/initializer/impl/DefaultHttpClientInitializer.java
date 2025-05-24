package org.turboweb.core.initializer.impl;

import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.client.config.HttpClientConfig;
import org.turboweb.core.initializer.HttpClientInitializer;
import org.turboweb.client.HttpClientUtils;

import java.util.function.Consumer;

/**
 * http客户端的初始化器
 */
public class DefaultHttpClientInitializer implements HttpClientInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpClientInitializer.class);
    private final HttpClientConfig config = new HttpClientConfig();

    @Override
    public void config(Consumer<HttpClientConfig> consumer) {
        consumer.accept(config);
    }

    @Override
    public void init(EventLoopGroup group) {
        HttpClientUtils.initClient(config, group);
        log.info("HttpClient初始化完成");
    }
}
