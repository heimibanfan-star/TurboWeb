package top.turboweb.core.initializer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.core.initializer.ProcessorInitializer;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.processor.CorsProcessor;
import top.turboweb.http.processor.ExceptionHandlerProcessor;
import top.turboweb.http.processor.MiddlewareInvokeProcessor;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.processor.convertor.DefaultHttpResponseConverter;
import top.turboweb.http.processor.convertor.HttpResponseConverter;
import top.turboweb.http.session.SessionManagerHolder;

/**
 * 默认的内核处理器初始化器
 */
public class DefaultProcessorInitializer implements ProcessorInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessorInitializer.class);
    private final HttpResponseConverter converter = new DefaultHttpResponseConverter();

    private final CorsProcessor corsProcessor = new CorsProcessor();

    @Override
    public Processor init(Middleware chain, SessionManagerHolder sessionManagerHolder, ExceptionHandlerMatcher matcher) {
        // 初始化剩余内核处理器
        MiddlewareInvokeProcessor middlewareInvokeProcessor = initMiddlewareInvokerProcessor(chain, sessionManagerHolder);
        ExceptionHandlerProcessor exceptionHandlerProcessor = initExceptionHandlerProcessor(middlewareInvokeProcessor, matcher);
        // 组合处理器链
        corsProcessor.setNextProcessor(exceptionHandlerProcessor);
        log.info("processor init success: cors -> exceptionHandler -> middlewareInvoke");
        return corsProcessor;
    }

    @Override
    public CorsProcessor.Config getCorsConfig() {
        return this.corsProcessor.getConfig();
    }

    /**
     * 初始化中间件调用的处理器
     *
     * @param chain 用户态的中间件链
     * @param sessionManagerHolder session管理器的持有对象
     * @return 中间件调用处理器
     */
    private MiddlewareInvokeProcessor initMiddlewareInvokerProcessor(Middleware chain, SessionManagerHolder sessionManagerHolder) {
        return new MiddlewareInvokeProcessor(chain, sessionManagerHolder, converter);
    }

    /**
     * 初始化异常处理器
     *
     * @param nextProcessor 下一个处理器(中间件调用处理器)
     * @param matcher 异常处理器匹配器
     * @return 异常处理器
     */
    private ExceptionHandlerProcessor initExceptionHandlerProcessor(MiddlewareInvokeProcessor nextProcessor, ExceptionHandlerMatcher matcher) {
        ExceptionHandlerProcessor exceptionHandlerProcessor = new ExceptionHandlerProcessor(matcher, converter);
        exceptionHandlerProcessor.setNextProcessor(nextProcessor);
        return exceptionHandlerProcessor;
    }
}
