package top.turboweb.http.handler;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.response.HttpResult;
import top.turboweb.http.exception.ExceptionHandlerException;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.orchestrator.ExceptionHandlerManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ExceptionHandlerManager} 的构造解析、匹配调度与兜底行为。
 *
 * <p>处理器实例经构造器传入集合并当场解析；处理方法按签名认领（公有、单异常参数），
 * 执行与否的观测靠夹具方法上的调用计数；
 * 兜底响应经 createResponse 落为 netty 响应后核对状态码、Content-Type 与异常栈正文。
 */
class ExceptionHandlerManagerTest {

    /** RuntimeException 处理器：计数并回传拼接信息。 */
    public static class RteHandler {
        public int calls;

        public String handle(RuntimeException e) {
            calls++;
            return "rte:" + e.getMessage();
        }
    }

    /** IllegalStateException 处理器：与 RuntimeException 处理器同时在册时验证就近匹配。 */
    public static class IseHandler {
        public int calls;

        public String handle(IllegalStateException e) {
            calls++;
            return "ise:" + e.getMessage();
        }
    }

    /** void 返回的受检异常处理器：证明执行后返回 null 而非兜底对象。 */
    public static class IoVoidHandler {
        public int calls;

        public void handle(IOException e) {
            calls++;
        }
    }

    /** 静态异常处理器：证明静态方法同样按签名认领且无需绑定实例。 */
    public static class StaticNpeHandler {
        public static int calls;

        public static String handle(NullPointerException e) {
            calls++;
            return "npe:" + e.getMessage();
        }
    }

    /** 执行即抛出的处理器：证明处理器自身失败走兜底。 */
    public static class ExplodingHandler {
        public int calls;

        public String handle(RuntimeException e) {
            calls++;
            throw new IllegalArgumentException("handler-fail");
        }
    }

    /** 参数类型或参数个数不符合签名的公有方法：解析时应全部忽略。 */
    public static class NotHandlerMethods {
        public String takesString(String value) {
            return value;
        }

        public String takesTwo(RuntimeException a, IllegalStateException b) {
            return a.getMessage();
        }
    }

    /** 断言兜底结果：500 状态码、text/html、正文含预期片段。 */
    private static void assertServerError(Object result, String... expectedInBody) {
        assertInstanceOf(HttpResult.class, result, "兜底应构建 HttpResult 对象");
        // 数据为字符串错误页，createResponse 不会触碰序列化器，传 null 引用安全
        HttpResponse response = ((HttpResult<?>) result).createResponse(null);
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.status());
        String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
        assertNotNull(contentType);
        assertTrue(contentType.startsWith("text/html"), contentType);
        String body = ((FullHttpResponse) response).content().toString(StandardCharsets.UTF_8);
        for (String expected : expectedInBody) {
            assertTrue(body.contains(expected), body);
        }
    }

    @Test
    @DisplayName("处理器实例集合为 null 时构造抛 NullPointerException")
    void constructorRejectsNullHandlerSet() {
        assertThrows(NullPointerException.class, () -> new ExceptionHandlerManager(null));
    }

    @Test
    @DisplayName("两级处理器同时在册时优先命中最近类型，子类异常沿继承链上溯")
    void handleMatchesClosestTypeFirst() {
        RteHandler rte = new RteHandler();
        IseHandler ise = new IseHandler();
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of(rte, ise));

        assertEquals("ise:s", manager.handle(new IllegalStateException("s")));
        assertEquals(1, ise.calls);
        assertEquals(0, rte.calls, "已命中子类型处理器，不应再向上执行父类处理器");

        assertEquals("rte:r", manager.handle(new RuntimeException("r")));

        // IllegalArgumentException 链上无专属处理器，上溯命中 RuntimeException 处理器
        assertEquals("rte:a", manager.handle(new IllegalArgumentException("a")));
        assertEquals(2, rte.calls);
    }

    @Test
    @DisplayName("void 处理器执行后返回 null，与兜底对象区分")
    void voidHandlerRunsAndReturnsNull() {
        IoVoidHandler io = new IoVoidHandler();
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of(io));

        assertNull(manager.handle(new IOException("io")));
        assertEquals(1, io.calls, "返回值 null 不排除已执行，计数证明执行发生");
    }

    @Test
    @DisplayName("静态方法按签名认领，执行走无绑定句柄")
    void staticHandlerMethodIsParsedAndInvoked() {
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of(new StaticNpeHandler()));

        int before = StaticNpeHandler.calls;
        assertEquals("npe:n", manager.handle(new NullPointerException("n")));
        assertEquals(before + 1, StaticNpeHandler.calls);
    }

    @Test
    @DisplayName("参数非异常类型或多参数的公有方法不认领，整体走兜底")
    void methodsOutsideSignatureAreIgnored() {
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of(new NotHandlerMethods()));

        assertServerError(manager.handle(new RuntimeException("nothing-registered")),
                "500 Internal Server Error",
                "java.lang.RuntimeException: nothing-registered");
    }

    @Test
    @DisplayName("处理器自身抛出异常时兜底 500，正文携带原始异常完整栈")
    void handlerFailureFallsBackToServerError() {
        ExplodingHandler exploding = new ExplodingHandler();
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of(exploding));

        Object result = manager.handle(new RuntimeException("in"));

        assertServerError(result,
                "500 Internal Server Error",
                "java.lang.RuntimeException: in");
        assertEquals(1, exploding.calls, "处理器已执行一次并抛出");
    }

    @Test
    @DisplayName("兜底页面异常栈经 HTML 转义，消息中的标记不透传为原始标签")
    void fallbackPageEscapesHtmlInStackText() {
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of());

        Object result = manager.handle(new RuntimeException("&<b>\"quote\"'apos'"));

        assertInstanceOf(HttpResult.class, result);
        HttpResponse response = ((HttpResult<?>) result).createResponse(null);
        String body = ((FullHttpResponse) response).content().toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("&amp;"), body);
        assertTrue(body.contains("&lt;b&gt;"), body);
        assertTrue(body.contains("&quot;quote&quot;"), body);
        assertTrue(body.contains("&#39;apos&#39;"), body);
        assertFalse(body.contains("<b>"), "转义后不得残留可执行的原始标签");
    }

    @Test
    @DisplayName("路由未匹配异常无处理器命中时兜底 404，消息经 HTML 转义")
    void routerNotMatchWithoutHandlerFallsBackToNotFound() {
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of());

        Object result = manager.handle(new RouteHandlerException(
                "router not found: GET /missing<b>", RouteHandlerException.ROUTER_NOT_MATCH));

        assertInstanceOf(HttpResult.class, result, "404 兜底应构建 HttpResult 对象");
        HttpResponse response = ((HttpResult<?>) result).createResponse(null);
        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
        String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
        assertNotNull(contentType);
        assertTrue(contentType.startsWith("text/html"), contentType);
        String body = ((FullHttpResponse) response).content().toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("404 Not Found"), body);
        assertTrue(body.contains("router not found: GET /missing&lt;b&gt;"), body);
        assertFalse(body.contains("<b>"), "转义后不得残留可执行的原始标签");
    }

    @Test
    @DisplayName("其余错误码的路由异常无处理器命中时仍兜底 500")
    void otherRouterErrorCodeStillFallsBackToServerError() {
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of());

        Object result = manager.handle(new RouteHandlerException(
                new IllegalStateException("invoke-fail"), RouteHandlerException.ROUTER_INVOKE_ERROR));

        assertServerError(result,
                "500 Internal Server Error",
                "invoke-fail");
    }

    @Test
    @DisplayName("两个实例声明相同异常类型时构造抛 ExceptionHandlerException")
    void duplicateExceptionTypeIsRejectedAtConstruction() {
        ExceptionHandlerException ex = assertThrows(ExceptionHandlerException.class,
                () -> new ExceptionHandlerManager(Set.of(new RteHandler(), new ExplodingHandler())));
        assertTrue(ex.getMessage().contains(RuntimeException.class.getName()), ex.getMessage());
    }
}
