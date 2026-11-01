package top.turboweb.http.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.response.HttpResult;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.exception.RouteHandlerException.ErrorCode;
import top.turboweb.http.orchestrator.ExceptionHandlerManager;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ExceptionHandlerManager} 的构造解析、匹配调度、结果归一化与兜底行为。
 *
 * <p>处理器实例经构造器传入集合并当场解析；处理方法按签名认领（公有、单异常参数），
 * 执行与否的观测靠夹具方法上的调用计数；返回值归一化口径：已是 HttpResult 原样透传，
 * 其余返回值包装为携带该值的 200 HttpResult；兜底结果核对状态行码与页面正文片段。</p>
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

    /** void 返回的受检异常处理器：证明执行发生且返回值归一化为 data 为 null 的 200 结果。 */
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

    /** 返回值已是 HttpResult 的处理器：证明该值原样透传不再包装。 */
    public static class HttpResultReturningHandler {
        public int calls;

        public HttpResult handle(RuntimeException e) {
            calls++;
            return HttpResult.err("boom");
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

    /** 断言兜底结果：500 状态行、数据为字符串错误页且含预期片段。 */
    private static void assertServerError(HttpResult result, String... expectedInBody) {
        assertEquals(500, result.statusLine().code(), "兜底应返回 500 状态行");
        String body = (String) result.data();
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

        HttpResult iseResult = manager.handle(new IllegalStateException("s"));
        assertEquals("ise:s", iseResult.data());
        assertEquals(200, iseResult.statusLine().code(), "非 HttpResult 返回值包装为 200 结果");
        assertEquals(1, ise.calls);
        assertEquals(0, rte.calls, "已命中子类型处理器，不应再向上执行父类处理器");

        assertEquals("rte:r", manager.handle(new RuntimeException("r")).data());

        // IllegalArgumentException 链上无专属处理器，上溯命中 RuntimeException 处理器
        assertEquals("rte:a", manager.handle(new IllegalArgumentException("a")).data());
        assertEquals(2, rte.calls);
    }

    @Test
    @DisplayName("void 处理器执行后返回 data 为 null 的 200 结果")
    void voidHandlerRunsAndWrapsNullData() {
        IoVoidHandler io = new IoVoidHandler();
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of(io));

        HttpResult result = manager.handle(new IOException("io"));

        assertEquals(200, result.statusLine().code());
        assertNull(result.data(), "void 返回值归一化为 data 为 null 的结果");
        assertEquals(1, io.calls, "返回值 null 不排除已执行，计数证明执行发生");
    }

    @Test
    @DisplayName("静态方法按签名认领，执行走无绑定句柄")
    void staticHandlerMethodIsParsedAndInvoked() {
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of(new StaticNpeHandler()));

        int before = StaticNpeHandler.calls;
        assertEquals("npe:n", manager.handle(new NullPointerException("n")).data());
        assertEquals(before + 1, StaticNpeHandler.calls);
    }

    @Test
    @DisplayName("返回值已是 HttpResult 的处理器，该值原样透传不再包装")
    void httpResultReturnIsPassedThroughUnwrapped() {
        HttpResultReturningHandler handler = new HttpResultReturningHandler();
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of(handler));

        HttpResult result = manager.handle(new RuntimeException("in"));

        assertEquals(500, result.statusLine().code(), "透传的处理结果保留其自带状态行");
        assertEquals("boom", result.data());
        assertEquals(1, handler.calls);
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

        HttpResult result = manager.handle(new RuntimeException("in"));

        assertServerError(result,
                "500 Internal Server Error",
                "java.lang.RuntimeException: in");
        assertEquals(1, exploding.calls, "处理器已执行一次并抛出");
    }

    @Test
    @DisplayName("兜底页面异常栈经 HTML 转义，消息中的标记不透传为原始标签")
    void fallbackPageEscapesHtmlInStackText() {
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of());

        HttpResult result = manager.handle(new RuntimeException("&<b>\"quote\"'apos'"));

        assertEquals(500, result.statusLine().code());
        String body = (String) result.data();
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

        HttpResult result = manager.handle(new RouteHandlerException(
                "router not found: GET /missing<b>", ErrorCode.RouterNotMatch));

        assertEquals(404, result.statusLine().code(), "404 兜底按错误码携带的状态行返回");
        String body = (String) result.data();
        assertTrue(body.contains("404 Not Found"), body);
        assertTrue(body.contains("router not found: GET /missing&lt;b&gt;"), body);
        assertFalse(body.contains("<b>"), "转义后不得残留可执行的原始标签");
    }

    @Test
    @DisplayName("缺少必要参数错误码的路由异常无处理器命中时兜底 400，消息页不带异常栈")
    void missingParamRouterErrorFallsBackToBadRequest() {
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of());

        HttpResult result = manager.handle(new RouteHandlerException(
                "校验失败:[q must not be null]", ErrorCode.MissingParamError));

        assertEquals(400, result.statusLine().code());
        String body = (String) result.data();
        assertTrue(body.contains("400 Bad Request"), body);
        assertTrue(body.contains("校验失败:[q must not be null]"), body);
    }

    @Test
    @DisplayName("其余错误码的路由异常无处理器命中时仍兜底 500")
    void otherRouterErrorCodeStillFallsBackToServerError() {
        ExceptionHandlerManager manager = new ExceptionHandlerManager(Set.of());

        assertServerError(manager.handle(new RouteHandlerException(
                new IllegalStateException("invoke-fail"), ErrorCode.RouterInvokeError)),
                "500 Internal Server Error",
                "invoke-fail");
    }

    @Test
    @DisplayName("两个实例声明相同异常类型时构造抛 IllegalStateException")
    void duplicateExceptionTypeIsRejectedAtConstruction() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ExceptionHandlerManager(Set.of(new RteHandler(), new ExplodingHandler())));
        assertTrue(ex.getMessage().contains(RuntimeException.class.getName()), ex.getMessage());
    }
}
