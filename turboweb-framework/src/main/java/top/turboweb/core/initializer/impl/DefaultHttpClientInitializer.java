package top.turboweb.core.initializer.impl;

import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.client.config.HttpClientConfig;
import top.turboweb.core.initializer.HttpClientInitializer;
import top.turboweb.client.HttpClientUtils;

import java.util.function.Consumer;

/**
 * http客户端的初始化器
 */
public class DefaultHttpClientInitializer implements HttpClientInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpClientInitializer.class);
    private HttpClientConfig config = new HttpClientConfig();

    @Override
    public void config(Consumer<HttpClientConfig> consumer) {
        consumer.accept(config);
    }

    @Override
    public void init(EventLoopGroup group) {
        HttpClientUtils.initClient(config, group);
        log.info("HttpClient初始化完成");
    }

    @Override
    public void replaceConfig(HttpClientConfig httpClientConfig) {
        this.config = httpClientConfig;
    }
}
