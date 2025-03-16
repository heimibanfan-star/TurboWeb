package org.turbo.web.core.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import org.turbo.web.utils.common.BeanUtils;

/**
 * jackson序列化初始化监听器
 */
public abstract class JacksonTurboServerListener implements TurboServerListener {
    @Override
    public void beforeTurboServerInit(ServerBootstrap serverBootstrap) {
        init(BeanUtils.getObjectMapper());
    }

    @Override
    public void afterTurboServerInit(ServerBootstrap serverBootstrap) {

    }

    @Override
    public void afterTurboServerStart() {

    }

    public abstract void init(ObjectMapper objectMapper);
}
