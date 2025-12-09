package top.turboweb.core.initializer;

import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.processor.CorsProcessor;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.session.SessionManagerHolder;

/**
 * 内核处理器的初始化器
 */
public interface ProcessorInitializer {

    /**
     * 初始化内核处理器链
     *
     * @param chain                用户态的中间件链
     * @param sessionManagerHolder session管理器的持有者对象
     * @param matcher              异常处理器匹配器
     * @return 内核处理器链
     */
    Processor init(
            Middleware chain,
            SessionManagerHolder sessionManagerHolder,
            ExceptionHandlerMatcher matcher
    );

    /**
     * 获取CORS的配置对象
     *
     * @return CORS配置对象
     */
    CorsProcessor.Config getCorsConfig();

    /**
     * 设置Json序列化器
     *
     * @param jsonSerializer Json序列化器
     */
    void setJsonSerializer(JsonSerializer jsonSerializer);
}
