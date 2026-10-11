package top.turboweb.http.processor;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.handler.anno.ExceptionHandler;
import top.turboweb.http.handler.anno.ExceptionResponseStatus;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.commons.serializer.JacksonJsonSerializer;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.handler.DefaultExceptionHandlerMatcher;
import top.turboweb.http.handler.ExceptionHandlerContainerInitHelper;
import top.turboweb.http.processor.convertor.DefaultHttpResponseConverter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExceptionHandlerProcessor} 的命中 / 回退 / 404 / 500 四条路径（spec §5：processor）。
 *
 * <p>下游处理器用一个"指定抛哪个异常"的 {@link Processor} 匿名子类驱动，异常处理器用真实的
 * {@link ExceptionHandlerContainerInitHelper} 扫描 {@link DemoHandler} 注册，不 mock 匹配器与容器。
 * 断言钉住：命中时状态码取注解值、体来自处理器方法；处理器自身抛异常时回退默认 500；
 * {@code ROUTER_NOT_MATCH} 走 404、其余走 500。
 */
class ExceptionHandlerProcessorTest {

    /** 真实异常处理器：一个命中并返回字符串，一个命中后自身抛异常，用来触发回退分支。 */
    public static class DemoHandler {

        @ExceptionHandler(IllegalStateException.class)
        @ExceptionResponseStatus(418)
        public Object handleState(IllegalStateException e) {
            return "handled:" + e.getMessage();
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public Object handleArgAndBoom(IllegalArgumentException e) {
            throw new IllegalStateException("handler failed");
        }
    }

    private static ExceptionHandlerProcessor processor() {
        DefaultExceptionHandlerMatcher matcher = new DefaultExceptionHandlerMatcher(
                ExceptionHandlerContainerInitHelper.initContainer(List.of(new DemoHandler())));
        DefaultHttpResponseConverter converter =
                new DefaultHttpResponseConverter(new JacksonJsonSerializer());
        return new ExceptionHandlerProcessor(matcher, converter);
    }

    /** 下游处理器：调用即抛出给定异常，用来让被测量的 try/catch 进入异常分支。 */
    private static void chainTo(ExceptionHandlerProcessor processor, final RuntimeException toThrow) {
        processor.setNextProcessor(new Processor() {
            @Override
            public HttpResponse invoke(FullHttpRequest request, ConnectSession connectSession) {
                throw toThrow;
            }
        });
    }

    private static FullHttpRequest request() {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/x");
    }

    private static String body(FullHttpResponse response) {
        return response.content().toString(GlobalConfig.getResponseCharset());
    }

    @Test
    @DisplayName("命中处理器：状态码取 @ExceptionResponseStatus，响应体来自处理器方法返回值")
    void matchedHandlerAppliesAnnotatedStatusAndConvertedBody() {
        ExceptionHandlerProcessor processor = processor();
        chainTo(processor, new IllegalStateException("x"));

        FullHttpResponse response = (FullHttpResponse) processor.invoke(request(), null);

        assertEquals(418, response.status().code());
        assertEquals("handled:x", body(response));
        assertEquals("text/html;charset=" + GlobalConfig.getResponseCharset().name(),
                response.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }

    @Test
    @DisplayName("处理器自身抛异常：回退默认处理器，返回 500 错误页")
    void handlerThatThrowsFallsBackToDefault500() {
        ExceptionHandlerProcessor processor = processor();
        chainTo(processor, new IllegalArgumentException("y"));

        FullHttpResponse response = (FullHttpResponse) processor.invoke(request(), null);

        assertEquals(500, response.status().code());
        assertTrue(body(response).contains("Code: 500"),
                "处理器失败必须回退默认 500，实际体=" + body(response));
    }

    @Test
    @DisplayName("无命中且为 ROUTER_NOT_MATCH：返回 404 而不是 500")
    void routerNotMatchWithoutHandlerBecomes404() {
        ExceptionHandlerProcessor processor = processor();
        chainTo(processor, new TurboRouterException("no route", TurboRouterException.ROUTER_NOT_MATCH));

        FullHttpResponse response = (FullHttpResponse) processor.invoke(request(), null);

        assertEquals(404, response.status().code());
        assertTrue(body(response).contains("Code: 404"),
                "路由未匹配必须 404，实际体=" + body(response));
    }

    @Test
    @DisplayName("无命中且为普通异常：返回 500")
    void otherExceptionWithoutHandlerBecomes500() {
        ExceptionHandlerProcessor processor = processor();
        chainTo(processor, new RuntimeException("boom"));

        FullHttpResponse response = (FullHttpResponse) processor.invoke(request(), null);

        assertEquals(500, response.status().code());
        assertTrue(body(response).contains("Code: 500"));
    }

    @Test
    @DisplayName("处理器链末端 next 返回 null（Processor 的末端分支）")
    void processorAtChainTailReturnsNull() {
        ExceptionHandlerProcessor processor = processor();

        assertNull(processor.invoke(request(), null));
    }
}
