package top.turboweb.http.router;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.rest.param.PathParam;
import top.turboweb.rest.route.Get;
import top.turboweb.rest.route.Post;
import top.turboweb.rest.route.Route;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RestRouterHandlerManager 单元测试:baseUrl 规整、四种注册入口的空值与重复拒绝、
 * build 的按 url 聚合与同路径同请求方式拒绝、请求分发(路径命中、查询串剥离、
 * 尾斜杠容忍、路径参数解码注入)、路由未命中的路由不匹配错误码、
 * 目标方法异常透传与非运行时异常的调用错误包装。
 */
class RestRouterHandlerManagerTest {

    public static class PingController {

        @Get("/ping")
        public String ping() {
            return "pong";
        }

        @Post("/ping")
        public String postPing() {
            return "posted";
        }
    }

    @Route("/u")
    public static class ParamController {

        @Get("/user/:id")
        public String user(@PathParam("id") int id) {
            return "id=" + id;
        }

        @Get("/name/:name")
        public String name(HttpContext ctx) {
            return "name=" + ctx.param("name");
        }
    }

    public static class ThrowingController {

        @Get("/io")
        public String io() throws IOException {
            throw new IOException("io-fail");
        }

        @Get("/rt")
        public String rt() {
            throw new IllegalStateException("rt-fail");
        }
    }

    /** 类上无 @Route、方法用缺省路径,三级都提不出段,合并退化成根路径 */
    public static class RootController {

        @Get
        public String root() {
            return "root";
        }
    }

    public static class RepeatController {

        @Get("/dup")
        public String first() {
            return "first";
        }

        @Get("/dup")
        public String second() {
            return "second";
        }
    }

    @Route("/p")
    public static class ParentController {

        @Get("/m")
        public String m() {
            return "parent";
        }
    }

    public static class ChildController extends ParentController {

        @Override
        public String m() {
            return "child";
        }
    }

    private static HttpContext ctx(HttpMethod method, String uri) {
        return new FullHttpContext(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri),
                null, null, null, null);
    }

    private static RouteHandlerManager built(Object... instances) {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        for (Object instance : instances) {
            builder.registerRoute(instance);
        }
        return builder.build();
    }

    @Test
    void registeredRouteReturnsHandlerResult() {
        RouteHandlerManager manager = built(new PingController());
        assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/ping")));
    }

    @Test
    void samePathDifferentVerbsDispatchToTheirOwnHandler() {
        RouteHandlerManager manager = built(new PingController());
        assertEquals("posted", manager.handle(ctx(HttpMethod.POST, "/ping")), "同一路径的请求方式各走各的调用器");
    }

    @Test
    void unmatchedVerbOnKnownPathThrowsRouterNotMatch() {
        RouteHandlerManager manager = built(new PingController());
        TurboRouterException error = assertThrows(TurboRouterException.class,
                () -> manager.handle(ctx(HttpMethod.PUT, "/ping")), "路径命中但请求方式无对应调用器仍按路由不匹配处理");
        assertEquals(TurboRouterException.ROUTER_NOT_MATCH, error.getCode());
    }

    @Test
    void unmatchedPathThrowsRouterNotMatchWithMessage() {
        RouteHandlerManager manager = built(new PingController());
        TurboRouterException error = assertThrows(TurboRouterException.class,
                () -> manager.handle(ctx(HttpMethod.GET, "/nope")), "路由找不到按路由不匹配错误码抛出,由异常处理器转 404");
        assertEquals(TurboRouterException.ROUTER_NOT_MATCH, error.getCode());
        assertEquals("router not found: GET /nope", error.getMessage(), "消息含请求方式与 uri,与原注解路由口径一致");
    }

    @Test
    void notFoundMessageKeepsQueryStringOfOriginalUri() {
        RouteHandlerManager manager = built(new PingController());
        TurboRouterException error = assertThrows(TurboRouterException.class,
                () -> manager.handle(ctx(HttpMethod.POST, "/nope?x=1")));
        assertEquals("router not found: POST /nope?x=1", error.getMessage(), "消息用原始 uri,查询串不参与匹配但仍呈现");
    }

    @Test
    void queryStringIsStrippedBeforeMatching() {
        RouteHandlerManager manager = built(new PingController());
        assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/ping?a=1&b=2")), "查询串不属路径,匹配前剥离");
    }

    @Test
    void trailingSlashInRequestPathStillMatches() {
        RouteHandlerManager manager = built(new PingController());
        assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/ping/")), "请求路径的尾斜杠由树的段归一吞掉");
    }

    @Test
    void pathParamIsBoundAndConverted() {
        RouteHandlerManager manager = built(new ParamController());
        assertEquals("id=42", manager.handle(ctx(HttpMethod.GET, "/u/user/42")), "路径参数解码后注入并绑定到形参");
    }

    @Test
    void encodedPathParamIsDecodedBeforeInjection() {
        RouteHandlerManager manager = built(new ParamController());
        assertEquals("name=a b", manager.handle(ctx(HttpMethod.GET, "/u/name/a%20b")), "百分号编码按请求字符集解码");
        assertEquals("name=a b", manager.handle(ctx(HttpMethod.GET, "/u/name/a+b")),
                "加号按表单口径解为空格,与原前缀树参数解码语义一致");
    }

    @Test
    void rootFormUrlIsPrefixedByBaseUrl() {
        RouteHandlerManager manager = new RestRouterHandlerManager.Builder("/api").registerRoute(new PingController()).build();
        assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/api/ping")));
    }

    @Test
    void baseUrlVariantsAreNormalizedToSamePrefix() {
        for (String baseUrl : List.of("api", "/api", "api/", "/api/", "/api//", "/api///")) {
            RouteHandlerManager manager = new RestRouterHandlerManager.Builder(baseUrl)
                    .registerRoute(new PingController()).build();
            assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/api/ping")), "前缀 " + baseUrl + " 规整为 /api");
        }
    }

    @Test
    void emptyRootAndNullBaseUrlYieldNoPrefix() {
        for (String baseUrl : Arrays.asList("", "/", "//", null)) {
            RouteHandlerManager manager = new RestRouterHandlerManager.Builder(baseUrl)
                    .registerRoute(new PingController()).build();
            assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/ping")), "前缀 " + baseUrl + " 视作根,不加前缀");
        }
    }

    @Test
    void rootPathControllerIsRejectedWhileBuilding() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RestRouterHandlerManager.Builder().registerRoute(new RootController()).build(),
                "各级都提不出段的根路由在合并环节即拒,不再撞基数树的空段异常、也不静默 404");
        assertTrue(error.getMessage().contains("不支持根路径路由"), "拒绝来自路径合并,消息可归因");
    }

    @Test
    void rootMethodUnderPrefixRegistersThePrefixItself() {
        RouteHandlerManager manager = new RestRouterHandlerManager.Builder("/api")
                .registerRoute(new RootController()).build();
        assertEquals("root", manager.handle(ctx(HttpMethod.GET, "/api")), "方法路径是根而前缀有段时,取前缀为路径");
    }

    @Test
    void explicitTargetClassDrivesAnnotationScanAndInstanceDrivesInvocation() {
        RouteHandlerManager manager = new RestRouterHandlerManager.Builder()
                .registerRoute(ParentController.class, new ChildController()).build();
        assertEquals("child", manager.handle(ctx(HttpMethod.GET, "/p/m")),
                "扫描按显式类取注解,调用落在传入实例上");
    }

    @Test
    void listOfInstancesRegistersEveryRoute() {
        RouteHandlerManager manager = new RestRouterHandlerManager.Builder()
                .registerRoute(List.of(new PingController(), new ParamController())).build();
        assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/ping")));
        assertEquals("id=7", manager.handle(ctx(HttpMethod.GET, "/u/user/7")), "同批注册的第二个控制器路由同样可用");
    }

    @Test
    void mapOfRoutesRegistersEveryRoute() {
        Map<Class<?>, Object> routes = new HashMap<>();
        routes.put(PingController.class, new PingController());
        routes.put(ParamController.class, new ParamController());
        RouteHandlerManager manager = new RestRouterHandlerManager.Builder().registerRoute(routes).build();
        assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/ping")));
        assertEquals("id=7", manager.handle(ctx(HttpMethod.GET, "/u/user/7")));
    }

    @Test
    void nullListIsTreatedAsNoRegistration() {
        RouteHandlerManager manager = new RestRouterHandlerManager.Builder().registerRoute((List<Object>) null).build();
        TurboRouterException error = assertThrows(TurboRouterException.class,
                () -> manager.handle(ctx(HttpMethod.GET, "/ping")), "没有任何注册时一切请求都是路由找不到");
        assertEquals(TurboRouterException.ROUTER_NOT_MATCH, error.getCode());
    }

    @Test
    void duplicateClassInListIsRejectedLikeMapEntry() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RestRouterHandlerManager.Builder().registerRoute(List.of(new PingController(), new PingController())),
                "同类重复注册统一走已注册拒绝,不再因入口不同而异");
        assertEquals(PingController.class + " is already registered", error.getMessage());
    }

    @Test
    void secondRegistrationOfSameClassIsRejected() {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder()
                .registerRoute(new PingController());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> builder.registerRoute(Map.of(PingController.class, new PingController())));
        assertEquals(PingController.class + " is already registered", error.getMessage());
    }

    @Test
    void nullArgumentsAreRejectedWithNamedMessages() {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        NullPointerException nullInstance = assertThrows(NullPointerException.class,
                () -> builder.registerRoute((Object) null));
        assertEquals("routeInstance must not be null", nullInstance.getMessage());
        NullPointerException nullClass = assertThrows(NullPointerException.class,
                () -> builder.registerRoute(null, new PingController()));
        assertEquals("targetClass must not be null", nullClass.getMessage());
        NullPointerException listWithNull = assertThrows(NullPointerException.class,
                () -> builder.registerRoute(Arrays.asList(new PingController(), null)));
        assertEquals("routeInstance must not be null", listWithNull.getMessage(), "列表里的空元素同样按实例判空消息失败");
        Map<Class<?>, Object> withNullValue = new HashMap<>();
        withNullValue.put(PingController.class, null);
        NullPointerException mapWithNullValue = assertThrows(NullPointerException.class,
                () -> builder.registerRoute(withNullValue));
        assertEquals("routeInstance must not be null", mapWithNullValue.getMessage());
        Map<Class<?>, Object> withNullKey = new HashMap<>();
        withNullKey.put(null, new PingController());
        NullPointerException mapWithNullKey = assertThrows(NullPointerException.class,
                () -> builder.registerRoute(withNullKey));
        assertEquals("targetClass must not be null", mapWithNullKey.getMessage(), "判空先于重复检查");
    }

    @Test
    void buildRejectsSameVerbTwiceOnOnePath() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RestRouterHandlerManager.Builder().registerRoute(new RepeatController()).build());
        assertEquals("Route repeat:GET /dup", error.getMessage(), "同路径同请求方式在构建期即拒绝");
    }

    @Test
    void runtimeExceptionFromTargetPropagatesUnwrapped() {
        RouteHandlerManager manager = built(new ThrowingController());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> manager.handle(ctx(HttpMethod.GET, "/rt")));
        assertEquals("rt-fail", error.getMessage(), "运行时异常原样透传,交上层异常处理");
    }

    @Test
    void checkedExceptionFromTargetIsWrappedAsInvokeError() {
        RouteHandlerManager manager = built(new ThrowingController());
        TurboRouterException error = assertThrows(TurboRouterException.class,
                () -> manager.handle(ctx(HttpMethod.GET, "/io")), "非运行时异常按调用错误码包装");
        assertEquals(TurboRouterException.ROUTER_INVOKE_ERROR, error.getCode());
        IOException cause = assertInstanceOf(IOException.class, error.getCause());
        assertEquals("io-fail", cause.getMessage(), "包装保留原因");
    }

    @Test
    void registrationMethodsReturnSameBuilder() {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder("/api");
        assertSame(builder, builder.registerRoute(new PingController()));
        assertSame(builder, builder.registerRoute(List.of(new ParamController())));
        assertSame(builder, builder.registerRoute(Map.of(ThrowingController.class, new ThrowingController())));
        assertSame(builder, builder.registerRoute(ParentController.class, new ChildController()));
    }
}
