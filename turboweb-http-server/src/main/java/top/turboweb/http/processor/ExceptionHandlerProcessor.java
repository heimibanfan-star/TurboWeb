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
 * 内核处理器：异常处理器。
 * <p>
 * 该处理器负责捕获 HTTP 请求处理链中抛出的异常，并调用对应的异常处理器进行处理，
 * 将异常转换为标准的 HTTP 响应返回给客户端。
 * </p>
 * <p>
 * 异常处理逻辑：
 * <ul>
 *     <li>调用下一个处理器处理请求</li>
 *     <li>若发生异常，查找匹配的异常处理器 {@link ExceptionHandlerMatcher}</li>
 *     <li>若匹配到自定义处理器，则调用对应方法并将结果转换为 {@link HttpResponse}</li>
 *     <li>若未匹配到或处理器执行失败，使用默认异常处理器生成 HTTP 错误响应</li>
 * </ul>
 * </p>
 */
public class ExceptionHandlerProcessor extends Processor {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlerProcessor.class);

    /** 异常处理器匹配器，用于根据异常类型获取处理器定义 */
    private final ExceptionHandlerMatcher exceptionHandlerMatcher;

    /** HTTP 响应转换器，将处理结果转换为 HttpResponse */
    private final HttpResponseConverter httpResponseConverter;

    /**
     * 构造方法
     *
     * @param exceptionHandlerMatcher 异常处理器匹配器
     * @param converter               响应转换器
     */
    public ExceptionHandlerProcessor(
            ExceptionHandlerMatcher exceptionHandlerMatcher,
            HttpResponseConverter converter
    ) {
        this.exceptionHandlerMatcher = exceptionHandlerMatcher;
        this.httpResponseConverter = converter;
    }

    /**
     * 处理请求。
     * <p>
     * 调用下一个处理器处理请求，如发生异常则交由 {@link #handleException(Throwable)} 处理。
     * </p>
     *
     * @param fullHttpRequest 请求对象
     * @param connectSession  当前连接会话
     * @return HTTP 响应
     */
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
     * 根据异常类型处理异常。
     * <p>
     * 通过 {@link ExceptionHandlerMatcher} 匹配对应的异常处理器定义，
     * 并使用 {@link MethodHandle} 调用处理方法，将结果转换为 HttpResponse。
     * </p>
     *
     * @param e 异常对象
     * @return HTTP 响应
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
     * 默认异常处理器。
     * <p>
     * 对特定路由未匹配异常返回 404，其他异常返回 500 错误响应。
     * </p>
     *
     * @param e 异常对象
     * @return HTTP 响应
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
     * 构建错误响应。
     *
     * @param content 响应内容
     * @param status  响应状态码
     * @return HTTP 响应对象
     */
    private HttpResponse buildErrResponse(String content, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.content().writeBytes(content.getBytes(GlobalConfig.getResponseCharset()));
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=" + GlobalConfig.getResponseCharset().name());
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }
}
