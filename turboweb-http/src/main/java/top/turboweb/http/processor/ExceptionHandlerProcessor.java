package top.turboweb.http.processor;

import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.commons.utils.base.ErrorStrGenerator;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.handler.ExceptionHandlerDefinition;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.processor.convertor.HttpResponseConverter;

import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * 异常处理器内核处理器
 */
public class ExceptionHandlerProcessor extends Processor {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlerProcessor.class);
    private final ExceptionHandlerMatcher exceptionHandlerMatcher;
    private final HttpResponseConverter httpResponseConverter;



    public ExceptionHandlerProcessor(
            ExceptionHandlerMatcher exceptionHandlerMatcher,
            HttpResponseConverter converter
    ) {
        this.exceptionHandlerMatcher = exceptionHandlerMatcher;
        this.httpResponseConverter = converter;
    }

    @Override
    public HttpResponse invoke(FullHttpRequest fullHttpRequest, ConnectSession connectSession) {
        // 调用下一个中间件
        try {
            return next(fullHttpRequest, connectSession);
        } catch (Throwable e) {
            return handleException(e);
        }
    }

    /**
     * 处理异常
     *
     * @param e 异常
     * @return 处理结果
     */
    private HttpResponse handleException(Throwable e) {
        // 获取异常处理器定义信息
        ExceptionHandlerDefinition definition = exceptionHandlerMatcher.match(e.getClass());
        if (definition != null) {
            // 获取响应状态码
            HttpResponseStatus status = definition.getHttpResponseStatus();
            // 获取方法句柄
            MethodHandle methodHandler = definition.getMethodHandler();
            // 处理异常
            try {
                Object result = methodHandler.invoke(e);
                // 转换响应结果
                HttpResponse httpResponse = httpResponseConverter.convertor(result);
                // 设置状态码
                httpResponse.setStatus(status);
                return httpResponse;
            } catch (Throwable ex) {
                return defaultExceptionHandler(e);
            }
        } else {
            return defaultExceptionHandler(e);
        }
    }

    /**
     * 默认异常处理器
     *
     * @param e 异常
     * @return 响应结果
     */
    private HttpResponse defaultExceptionHandler(Throwable e) {
        if (e instanceof TurboRouterException turboRouterException && TurboRouterException.ROUTER_NOT_MATCH.equals(turboRouterException.getCode())) {
            // 生成异常信息
            String errMessage = ErrorStrGenerator.errHtml(404, e.getMessage());
            return buildErrResponse(errMessage, HttpResponseStatus.NOT_FOUND);
        }
        log.error("服务器异常", e);
        String errMessage = ErrorStrGenerator.errHtml(500, e.getMessage());
        return buildErrResponse(errMessage, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 构建错误响应
     *
     * @param content 响应内容
     * @param status  响应状态码
     * @return 响应结果
     */
    private HttpResponse buildErrResponse(String content, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.content().writeBytes(content.getBytes(GlobalConfig.getResponseCharset()));
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=" + GlobalConfig.getResponseCharset().name());
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }
}
