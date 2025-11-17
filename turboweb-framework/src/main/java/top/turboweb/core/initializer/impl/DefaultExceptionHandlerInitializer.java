package top.turboweb.core.initializer.impl;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.http.handler.DefaultExceptionHandlerMatcher;
import top.turboweb.http.handler.ExceptionHandlerContainer;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.core.initializer.ExceptionHandlerInitializer;
import top.turboweb.http.handler.ExceptionHandlerContainerInitHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认的异常处理器初始化器
 */
public class DefaultExceptionHandlerInitializer implements ExceptionHandlerInitializer {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(DefaultExceptionHandlerInitializer.class);

    // 存储异常处理器
    private final List<Object> exceptionHandlerList = new ArrayList<>();

    @Override
    public void addExceptionHandler(Object... exceptionHandler) {
        exceptionHandlerList.addAll(List.of(exceptionHandler));
    }

    @Override
    public ExceptionHandlerMatcher init() {
        ExceptionHandlerContainer container = ExceptionHandlerContainerInitHelper.initContainer(exceptionHandlerList);
        ExceptionHandlerMatcher matcher = new DefaultExceptionHandlerMatcher(container);
        log.info("异常处理器初始化成功");
        return matcher;
    }
}
