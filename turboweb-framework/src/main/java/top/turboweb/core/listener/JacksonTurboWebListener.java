package top.turboweb.core.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.turboweb.commons.utils.base.BeanUtils;

/**
 * jackson序列化初始化监听器
 */
public abstract class JacksonTurboWebListener implements TurboWebListener {
    @Override
    public void beforeServerInit() {
        init(BeanUtils.getObjectMapper());
    }

    @Override
    public void afterServerStart() {

    }

    public abstract void init(ObjectMapper objectMapper);
}
