package top.turboweb.http.router;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.router.parameter.ParameterParser.Binder;
import top.turboweb.http.router.parameter.ParameterParser;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RouteHandler 单元测试:元信息空值与重复请求方式拒绝、混合路径元信息拒绝、
 * 同一请求方式的重复认领拒绝、按请求方式分发、未匹配抛路由不匹配错误码、
 * 参数解析器链顺序认领与缺省空绑定、方法句柄展开传参、目标异常透传、
 * 包装为定义创建异常、解析器列表判空
 */
class RouteHandlerTest {

    public static class EchoController {

        public String noArgs() {
            return "fixed";
        }

        public String echo(String a) {
            return "echo:" + a;
        }

        public String join(String a, String b) {
            return a + "/" + b;
        }

        public String getHandler() {
            return "get";
        }

        public String postHandler() {
            return "post";
        }

        public int add(int a, int b) {
            return a + b;
        }

        public String boom() {
            throw new IllegalStateException("boom");
        }

        private String hidden() {
            return "hidden";
        }
    }

    private static RouteHandlerMetaInfo meta(String url, Object instance, HttpMethod httpMethod,
                                             String name, Class<?>... types) {
        try {
            Method method = instance.getClass().getMethod(name, types);
            return new RouteHandlerMetaInfo(url, instance, instance.getClass(), method, httpMethod);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static RouteHandlerMetaInfo accessiblePrivateMeta(String url, Object instance, HttpMethod httpMethod,
                                                              String name) {
        try {
            Method method = instance.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return new RouteHandlerMetaInfo(url, instance, instance.getClass(), method, httpMethod);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static RouteHandlerMetaInfo rawPrivateMeta(String url, Object instance, HttpMethod httpMethod,
                                                       String name) {
        try {
            Method method = instance.getClass().getDeclaredMethod(name);
            return new RouteHandlerMetaInfo(url, instance, instance.getClass(), method, httpMethod);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static HttpContext ctx(HttpMethod method, String uri) {
        return new FullHttpContext(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri),
                null, null, null, null, StandardCharsets.UTF_8);
    }

    private static final ParameterParser REJECTING = parameter -> null;

    /** 认领所有参数,绑定值为前缀加认领顺序下标 */
    private static ParameterParser claiming(String prefix) {
        return new ParameterParser() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public ParameterParser.Binder parse(Parameter parameter) {
                int current = counter.getAndIncrement();
                return ctx -> prefix + current;
            }
        };
    }

    @Test
    void emptyAndNullMetaInfosRejected() {
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> new RouteHandler(List.of(), List.of()));
        assertEquals("RouteHandlerMetaInfo must not be empty", empty.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new RouteHandler(null, List.of()));
    }

    @Test
    void nullParsersRejectedWithClearMessage() {
        EchoController controller = new EchoController();
        NullPointerException zeroArg = assertThrows(NullPointerException.class,
                () -> new RouteHandler(List.of(meta("/x", controller, HttpMethod.GET, "noArgs")), null));
        assertEquals("parsers must not be null", zeroArg.getMessage(), "无参方法也要在构造入口拒绝空解析器列表");
        NullPointerException withParam = assertThrows(NullPointerException.class,
                () -> new RouteHandler(List.of(meta("/x", controller, HttpMethod.GET, "echo", String.class)), null));
        assertEquals("parsers must not be null", withParam.getMessage(), "空解析器列表的失败点与消息不因方法形状而异");
    }

    @Test
    void inconsistentUrlsAcrossMetaInfosRejected() {
        EchoController controller = new EchoController();
        List<RouteHandlerMetaInfo> metas = List.of(
                meta("/x", controller, HttpMethod.GET, "noArgs"),
                meta("/y", controller, HttpMethod.POST, "postHandler"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RouteHandler(metas, List.of()));
        assertEquals("Route url must be consistent: /x != /y", error.getMessage(),
                "一个处理器只承载一条路径,混入不同 url 在构造期拒绝");
    }

    @Test
    void duplicateHttpMethodRejected() {
        EchoController controller = new EchoController();
        List<RouteHandlerMetaInfo> metas = List.of(
                meta("/x", controller, HttpMethod.GET, "echo", String.class),
                meta("/x", controller, HttpMethod.GET, "boom"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RouteHandler(metas, List.of(REJECTING)));
        assertEquals("Route repeat:GET /x", error.getMessage(), "同一请求方式重复注册被拒绝");
    }

    @Test
    void dispatchesByRequestHttpMethod() throws Throwable {
        EchoController controller = new EchoController();
        RouteHandler handler = new RouteHandler(List.of(
                meta("/x", controller, HttpMethod.POST, "postHandler"),
                meta("/x", controller, HttpMethod.GET, "getHandler")), List.of());
        assertEquals("get", handler.handle(ctx(HttpMethod.GET, "/x")));
        assertEquals("post", handler.handle(ctx(HttpMethod.POST, "/x")), "按请求方式命中对应处理器,与注册顺序无关");
    }

    @Test
    void routeNotMatchThrowsWithCodeAndMessage() {
        EchoController controller = new EchoController();
        RouteHandler handler = new RouteHandler(List.of(meta("/x", controller, HttpMethod.GET, "getHandler")), List.of());
        RouteHandlerException error = assertThrows(RouteHandlerException.class,
                () -> handler.handle(ctx(HttpMethod.PUT, "/x")));
        assertEquals("not found route:PUT /x", error.getMessage(), "错误消息含请求方式与 uri");
        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, error.getCode(), "无匹配请求方式走路由不匹配错误码");
    }

    @Test
    void pathUsesFirstMetaUrl() {
        EchoController controller = new EchoController();
        RouteHandler handler = new RouteHandler(List.of(meta("/x", controller, HttpMethod.GET, "noArgs")), List.of());
        assertEquals("/x", handler.path());
    }

    @Test
    void zeroArgMethodInvoked() throws Throwable {
        EchoController controller = new EchoController();
        RouteHandler handler = new RouteHandler(List.of(meta("/x", controller, HttpMethod.GET, "noArgs")), List.of());
        assertEquals("fixed", handler.handle(ctx(HttpMethod.GET, "/x")), "无参方法不触碰解析器");
    }

    @Test
    void parsedParametersSpreadIntoInvocation() throws Throwable {
        EchoController controller = new EchoController();
        RouteHandler handler = new RouteHandler(
                List.of(meta("/x", controller, HttpMethod.GET, "join", String.class, String.class)),
                List.of(claiming("p")));
        assertEquals("p0/p1", handler.handle(ctx(HttpMethod.GET, "/x")), "参数按声明顺序绑定并展开传入");
    }

    @Test
    void unclaimedParameterFallsBackToNullBinder() throws Throwable {
        EchoController controller = new EchoController();
        RouteHandler handler = new RouteHandler(
                List.of(meta("/x", controller, HttpMethod.GET, "echo", String.class)),
                List.of(REJECTING));
        assertEquals("echo:null", handler.handle(ctx(HttpMethod.GET, "/x")), "无人认领的参数绑定为空");
    }

    @Test
    void firstClaimingParserWinsAndLaterParsersSkipped() throws Throwable {
        EchoController controller = new EchoController();
        ParameterParser exploding = parameter -> {
            throw new AssertionError("已被认领的参数不应继续向后解析");
        };
        RouteHandler handler = new RouteHandler(
                List.of(meta("/x", controller, HttpMethod.GET, "echo", String.class)),
                List.of(REJECTING, claiming("c"), exploding));
        assertEquals("echo:c0", handler.handle(ctx(HttpMethod.GET, "/x")), "解析器按注册序尝试,首个非空绑定生效");
    }

    @Test
    void primitiveParameterWithNullBinderThrowsOnUnboxing() {
        EchoController controller = new EchoController();
        RouteHandler handler = new RouteHandler(
                List.of(meta("/x", controller, HttpMethod.GET, "add", int.class, int.class)),
                List.of(REJECTING));
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> handler.handle(ctx(HttpMethod.GET, "/x")),
                "无人认领的原始类型参数绑定为空,调用时空值拆箱失败");
        assertTrue(error.getMessage().contains("intValue"), "空指针原因指向拆箱");
    }

    @Test
    void targetExceptionPropagates() {
        EchoController controller = new EchoController();
        RouteHandler handler = new RouteHandler(List.of(meta("/x", controller, HttpMethod.GET, "boom")), List.of());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> handler.handle(ctx(HttpMethod.GET, "/x")));
        assertEquals("boom", error.getMessage(), "目标方法异常原样透传");
    }

    @Test
    void accessiblePrivateMethodIsInvokable() throws Throwable {
        EchoController controller = new EchoController();
        RouteHandler handler = new RouteHandler(
                List.of(accessiblePrivateMeta("/x", controller, HttpMethod.GET, "hidden")), List.of());
        assertEquals("hidden", handler.handle(ctx(HttpMethod.GET, "/x")), "已开放访问的私有方法可正常展开调用");
    }

    @Test
    void inaccessiblePrivateMethodWrappedInDefinitionCreateException() {
        EchoController controller = new EchoController();
        RouteHandlerException error = assertThrows(RouteHandlerException.class,
                () -> new RouteHandler(List.of(rawPrivateMeta("/x", controller, HttpMethod.GET, "hidden")), List.of()));
        assertInstanceOf(IllegalAccessException.class, error.getCause(), "展开失败包装为路由定义创建异常并保留原因");
    }

    @Test
    void consistentUrlsWithDistinctMethodsPasses() {
        EchoController controller = new EchoController();
        RouteHandler handler = new RouteHandler(List.of(
                meta("/x", controller, HttpMethod.GET, "getHandler"),
                meta("/x", controller, HttpMethod.POST, "postHandler"),
                meta("/x", controller, HttpMethod.DELETE, "noArgs")), List.of());
        assertEquals("/x", handler.path(), "同一路径下多种请求方式合法");
    }

}
