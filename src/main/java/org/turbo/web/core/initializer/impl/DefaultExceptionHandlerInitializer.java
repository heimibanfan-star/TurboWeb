package org.turbo.web.core.initializer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.http.handler.DefaultExceptionHandlerMatcher;
import org.turbo.web.core.http.handler.ExceptionHandlerContainer;
import org.turbo.web.core.http.handler.ExceptionHandlerMatcher;
import org.turbo.web.core.initializer.ExceptionHandlerInitializer;
import org.turbo.web.utils.init.ExceptionHandlerContainerInitUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认的异常处理器初始化器
 */
public class DefaultExceptionHandlerInitializer implements ExceptionHandlerInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultExceptionHandlerInitializer.class);

    // 存储异常处理器
    private final List<Object> exceptionHandlerList = new ArrayList<>();

    @Override
    public void addExceptionHandler(Object... exceptionHandler) {
        exceptionHandlerList.addAll(List.of(exceptionHandler));
    }

    @Override
    public ExceptionHandlerMatcher init() {
        ExceptionHandlerContainer container = ExceptionHandlerContainerInitUtils.initContainer(exceptionHandlerList);
        ExceptionHandlerMatcher matcher = new DefaultExceptionHandlerMatcher(container);
        log.info("异常处理器初始化成功");
        return matcher;
    }
}
