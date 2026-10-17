package top.turboweb.http.router;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.exception.RouteHandlerException.ErrorCode;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.testkit.Contexts;
import top.turboweb.rest.parameter.BeanQuery;
import top.turboweb.rest.parameter.PathParam;
import top.turboweb.rest.parameter.QueryParam;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.rest.router.Get;
import top.turboweb.rest.router.Post;
import top.turboweb.rest.router.Route;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
 * RestRouteRegister 生产出的管理器单元测试:baseUrl 规整、四种注册入口的空值与重复拒绝、
 * 生产环节的按 url 聚合与同路径同请求方式拒绝、请求分发(路径命中、查询串剥离、
 * 尾斜杠容忍、路径参数解码绑定)、路由未命中的路由不匹配错误码、
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
        public String name(@PathParam("name") String name) {
            return "name=" + name;
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
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
        return new FullHttpContext(Contexts.message(request, StandardCharsets.UTF_8),
                null, null, null, StandardCharsets.UTF_8);
    }

    private static RouteHandlerManager built(Object... instances) {
        RestRouteRegister register = new RestRouteRegister();
        for (Object instance : instances) {
            register.registerRoute(instance);
        }
        return register.newRouteHandlerManager(new JacksonJsonSerializer());
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
        RouteHandlerException error = assertThrows(RouteHandlerException.class,
                () -> manager.handle(ctx(HttpMethod.PUT, "/ping")), "路径命中但请求方式无对应调用器仍按路由不匹配处理");
        assertEquals(ErrorCode.RouterNotMatch, error.getCode());
    }

    @Test
    void unmatchedPathThrowsRouterNotMatchWithMessage() {
        RouteHandlerManager manager = built(new PingController());
        RouteHandlerException error = assertThrows(RouteHandlerException.class,
                () -> manager.handle(ctx(HttpMethod.GET, "/nope")), "路由找不到按路由不匹配错误码抛出,由异常处理器转 404");
        assertEquals(ErrorCode.RouterNotMatch, error.getCode());
        assertEquals("router not found: GET /nope", error.getMessage(), "消息含请求方式与 uri,与原注解路由口径一致");
    }

    @Test
    void notFoundMessageKeepsQueryStringOfOriginalUri() {
        RouteHandlerManager manager = built(new PingController());
        RouteHandlerException error = assertThrows(RouteHandlerException.class,
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
        assertEquals("id=42", manager.handle(ctx(HttpMethod.GET, "/u/user/42")), "路径参数解码后经绑定器传入形参");
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
        RouteHandlerManager manager = new RestRouteRegister("/api")
                .registerRoute(new PingController())
                .newRouteHandlerManager(new JacksonJsonSerializer());
        assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/api/ping")));
    }

    @Test
    void baseUrlVariantsAreNormalizedToSamePrefix() {
        for (String baseUrl : List.of("api", "/api", "api/", "/api/", "/api//", "/api///")) {
            RouteHandlerManager manager = new RestRouteRegister(baseUrl)
                    .registerRoute(new PingController())
                    .newRouteHandlerManager(new JacksonJsonSerializer());
            assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/api/ping")), "前缀 " + baseUrl + " 规整为 /api");
        }
    }

    @Test
    void emptyRootAndNullBaseUrlYieldNoPrefix() {
        for (String baseUrl : Arrays.asList("", "/", "//", null)) {
            RouteHandlerManager manager = new RestRouteRegister(baseUrl)
                    .registerRoute(new PingController())
                    .newRouteHandlerManager(new JacksonJsonSerializer());
            assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/ping")), "前缀 " + baseUrl + " 视作根,不加前缀");
        }
    }

    @Test
    void rootPathControllerIsRejectedWhileBuilding() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RestRouteRegister().registerRoute(new RootController())
                        .newRouteHandlerManager(new JacksonJsonSerializer()),
                "各级都提不出段的根路由在合并环节即拒,不再撞基数树的空段异常、也不静默 404");
        assertTrue(error.getMessage().contains("root path routing is not supported"), "拒绝来自路径合并,消息可归因");
    }

    @Test
    void rootMethodUnderPrefixRegistersThePrefixItself() {
        RouteHandlerManager manager = new RestRouteRegister("/api")
                .registerRoute(new RootController())
                .newRouteHandlerManager(new JacksonJsonSerializer());
        assertEquals("root", manager.handle(ctx(HttpMethod.GET, "/api")), "方法路径是根而前缀有段时,取前缀为路径");
    }

    @Test
    void explicitTargetClassDrivesAnnotationScanAndInstanceDrivesInvocation() {
        RouteHandlerManager manager = new RestRouteRegister()
                .registerRoute(ParentController.class, new ChildController())
                .newRouteHandlerManager(new JacksonJsonSerializer());
        assertEquals("child", manager.handle(ctx(HttpMethod.GET, "/p/m")),
                "扫描按显式类取注解,调用落在传入实例上");
    }

    @Test
    void listOfInstancesRegistersEveryRoute() {
        RouteHandlerManager manager = new RestRouteRegister()
                .registerRoute(List.of(new PingController(), new ParamController()))
                .newRouteHandlerManager(new JacksonJsonSerializer());
        assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/ping")));
        assertEquals("id=7", manager.handle(ctx(HttpMethod.GET, "/u/user/7")), "同批注册的第二个控制器路由同样可用");
    }

    @Test
    void mapOfRoutesRegistersEveryRoute() {
        Map<Class<?>, Object> routes = new HashMap<>();
        routes.put(PingController.class, new PingController());
        routes.put(ParamController.class, new ParamController());
        RouteHandlerManager manager = new RestRouteRegister().registerRoute(routes)
                .newRouteHandlerManager(new JacksonJsonSerializer());
        assertEquals("pong", manager.handle(ctx(HttpMethod.GET, "/ping")));
        assertEquals("id=7", manager.handle(ctx(HttpMethod.GET, "/u/user/7")));
    }

    @Test
    void nullListIsTreatedAsNoRegistration() {
        RouteHandlerManager manager = new RestRouteRegister().registerRoute((List<Object>) null)
                .newRouteHandlerManager(new JacksonJsonSerializer());
        RouteHandlerException error = assertThrows(RouteHandlerException.class,
                () -> manager.handle(ctx(HttpMethod.GET, "/ping")), "没有任何注册时一切请求都是路由找不到");
        assertEquals(ErrorCode.RouterNotMatch, error.getCode());
    }

    @Test
    void duplicateClassInListIsRejectedLikeMapEntry() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new RestRouteRegister().registerRoute(List.of(new PingController(), new PingController())),
                "同类重复注册统一走已注册拒绝,不再因入口不同而异");
        assertEquals(PingController.class + " is already registered", error.getMessage());
    }

    @Test
    void secondRegistrationOfSameClassIsRejected() {
        RestRouteRegister register = new RestRouteRegister()
                .registerRoute(new PingController());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> register.registerRoute(Map.of(PingController.class, new PingController())));
        assertEquals(PingController.class + " is already registered", error.getMessage());
    }

    @Test
    void nullArgumentsAreRejectedWithNamedMessages() {
        RestRouteRegister register = new RestRouteRegister();
        NullPointerException nullInstance = assertThrows(NullPointerException.class,
                () -> register.registerRoute((Object) null));
        assertEquals("routeInstance must not be null", nullInstance.getMessage());
        NullPointerException nullClass = assertThrows(NullPointerException.class,
                () -> register.registerRoute(null, new PingController()));
        assertEquals("targetClass must not be null", nullClass.getMessage());
        NullPointerException listWithNull = assertThrows(NullPointerException.class,
                () -> register.registerRoute(Arrays.asList(new PingController(), null)));
        assertEquals("routeInstance must not be null", listWithNull.getMessage(), "列表里的空元素同样按实例判空消息失败");
        Map<Class<?>, Object> withNullValue = new HashMap<>();
        withNullValue.put(PingController.class, null);
        NullPointerException mapWithNullValue = assertThrows(NullPointerException.class,
                () -> register.registerRoute(withNullValue));
        assertEquals("routeInstance must not be null", mapWithNullValue.getMessage());
        Map<Class<?>, Object> withNullKey = new HashMap<>();
        withNullKey.put(null, new PingController());
        NullPointerException mapWithNullKey = assertThrows(NullPointerException.class,
                () -> register.registerRoute(withNullKey));
        assertEquals("targetClass must not be null", mapWithNullKey.getMessage(), "判空先于重复检查");
    }

    @Test
    void buildRejectsSameVerbTwiceOnOnePath() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RestRouteRegister().registerRoute(new RepeatController())
                        .newRouteHandlerManager(new JacksonJsonSerializer()));
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
        RouteHandlerException error = assertThrows(RouteHandlerException.class,
                () -> manager.handle(ctx(HttpMethod.GET, "/io")), "非运行时异常按调用错误码包装");
        assertEquals(ErrorCode.RouterInvokeError, error.getCode());
        IOException cause = assertInstanceOf(IOException.class, error.getCause());
        assertEquals("io-fail", cause.getMessage(), "包装保留原因");
    }

    public static class QueryController {

        @Get("/q")
        public String q(@QueryParam("name") String name) {
            return "name=" + name;
        }

        @Get("/flag")
        public String flag(@QueryParam("flag") String flag) {
            return "flag=" + flag;
        }
    }

    private static HttpContext ctx(HttpMethod method, String uri, Charset charset) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
        return new FullHttpContext(Contexts.message(request, charset),
                null, null, null, charset);
    }

    @Test
    void queryParameterFlowsThroughCarrierIntoBinder() {
        RouteHandlerManager manager = built(new QueryController());
        assertEquals("name=alice", manager.handle(ctx(HttpMethod.GET, "/q?name=alice")),
                "查询参数由装配解析、经承载对象随调用绑定,不再经上下文");
    }

    @Test
    void queryValueIsPercentDecoded() {
        RouteHandlerManager manager = built(new QueryController());
        assertEquals("name=a b", manager.handle(ctx(HttpMethod.GET, "/q?name=a%20b")),
                "百分号解码随 UTF-8 缺省字符集,旧口径兼容即零回归");
    }

    @Test
    void queryPercentEscapeFollowsDefaultCharset() {
        RouteHandlerManager manager = built(new QueryController());
        // D6 D0 是 GBK 下 "中" 的字节序
        assertEquals("name=中", manager.handle(ctx(HttpMethod.GET, "/q?name=%D6%D0", Charset.forName("GBK"))),
                "查询串解码字符集随 defaultCharset,与路径参数同尺");
    }

    @Test
    void queryFlagSegmentWithoutEqualsBindsNull() {
        RouteHandlerManager manager = built(new QueryController());
        assertEquals("flag=null", manager.handle(ctx(HttpMethod.GET, "/flag?flag")),
                "无等号段首值 null,String 转换器直通");
    }

    @Test
    void malformedQueryEscapeThrowsParamDecodeError() {
        RouteHandlerManager manager = built(new QueryController());
        RouteHandlerException error = assertThrows(RouteHandlerException.class,
                () -> manager.handle(ctx(HttpMethod.GET, "/q?name=%zz")), "畸形转义不再吞错当空参");
        assertEquals(ErrorCode.ParamDecodeError, error.getCode());
        assertEquals(HttpResponseStatus.BAD_REQUEST, error.getCode().getHttpStatus());
        assertInstanceOf(IllegalArgumentException.class, error.getCause(), "原始解码异常保留为 cause");
    }

    @Test
    void malformedPathParameterEscapeThrowsParamDecodeError() {
        RouteHandlerManager manager = built(new ParamController());
        RouteHandlerException error = assertThrows(RouteHandlerException.class,
                () -> manager.handle(ctx(HttpMethod.GET, "/u/user/%zz")), "路径段畸形转义不再裸逃逸 500");
        assertEquals(ErrorCode.ParamDecodeError, error.getCode());
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    @Test
    void unmatchedPathWithMalformedQueryStillReportsRouterNotMatch() {
        RouteHandlerManager manager = built(new QueryController());
        RouteHandlerException error = assertThrows(RouteHandlerException.class,
                () -> manager.handle(ctx(HttpMethod.GET, "/nope?x=%zz")), "路由匹配先于解码,未命中不做解码劳动");
        assertEquals(ErrorCode.RouterNotMatch, error.getCode());
    }

    public static class QbController {

        @Get("/qb")
        public String qb(@BeanQuery Qb bean) {
            return bean.name + "/" + (bean.tags == null ? "-" : String.join(",", bean.tags));
        }
    }

    public static class Qb {
        private String name;
        private List<String> tags;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    @Test
    void beanQueryBindsFromCarrierEndToEnd() {
        RouteHandlerManager manager = built(new QbController());
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/qb?name=alice&tags=a&tags=b");
        HttpContext context = new FullHttpContext(Contexts.message(req, StandardCharsets.UTF_8),
                null, null, null, StandardCharsets.UTF_8);
        assertEquals("alice/a,b", manager.handle(context),
                "@BeanQuery 端到端:承载查询映射经序列化器折叠绑定,数据源断链时该断言必红");
    }

    @Test
    void registrationMethodsReturnSameRegister() {
        RestRouteRegister register = new RestRouteRegister("/api");
        assertSame(register, register.registerRoute(new PingController()));
        assertSame(register, register.registerRoute(List.of(new ParamController())));
        assertSame(register, register.registerRoute(Map.of(ThrowingController.class, new ThrowingController())));
        assertSame(register, register.registerRoute(ParentController.class, new ChildController()));
    }
}
