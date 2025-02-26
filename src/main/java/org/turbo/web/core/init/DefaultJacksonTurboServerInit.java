package org.turbo.web.core.init;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.text.SimpleDateFormat;

/**
 * 默认的jackson初始化器
 */
public class DefaultJacksonTurboServerInit extends JacksonTurboServerInit{

    @Override
    public void init(ObjectMapper objectMapper) {
        objectMapper.registerModule(new JavaTimeModule());
        String pattern = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        objectMapper.setDateFormat(simpleDateFormat);
    }
}
