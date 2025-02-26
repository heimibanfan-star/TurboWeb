package org.turbo.web.core.init;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import org.turbo.web.utils.common.BeanUtils;

/**
 * jackson序列化初始化器
 */
public abstract class JacksonTurboServerInit implements TurboServerInit{
    @Override
    public void beforeTurboServerInit(ServerBootstrap serverBootstrap) {

    }

    @Override
    public void afterTurboServerInit(ServerBootstrap serverBootstrap) {
        init(BeanUtils.getObjectMapper());
    }

    @Override
    public void afterTurboServerStart() {

    }

    public abstract void init(ObjectMapper objectMapper);
}
