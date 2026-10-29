package top.turboweb.http.router;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.annotation.router.Delete;
import top.turboweb.http.core.annotation.router.Get;
import top.turboweb.http.core.annotation.router.Patch;
import top.turboweb.http.core.annotation.router.Post;
import top.turboweb.http.core.annotation.router.Put;
import top.turboweb.http.core.annotation.router.Route;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RouteHandlerMetaInfoHelper.parse 单元测试:三级路径合并(baseUrl + 类级 Route +
 * 方法级请求方式注解)、公有方法注解扫描、多动词注解拒绝、参数判空与 targetClass
 * 回落实例类、url 标准化(补前导斜杠、裁除尾斜杠)、连续斜杠非法路径拒绝、各级都提不出段时
 * 根路径不受支持的拒绝与输出排序。
 */
class RouteHandlerMetaInfoHelperTest {

    @Route("/hello")
    public static class HelloController {

        @Get("/1")
        public String hello() {
            return "hi";
        }
    }

    @Test
    void threeSegmentPathIsMergedInOrder() {
        HelloController instance = new HelloController();
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("/api", instance, HelloController.class);
        assertEquals(1, result.size(), "只产出一条标注方法的元信息");
        RouteHandlerMetaInfo meta = result.get(0);
        assertEquals("/api/hello/1", meta.url(), "三级路径按序合并且无多余斜杠");
        assertSame(instance, meta.instance());
        assertSame(HelloController.class, meta.routeClass());
        assertEquals("hello", meta.method().getName());
        assertEquals(0, meta.method().getParameterCount());
        assertSame(HttpMethod.GET, meta.httpMethod(), "Get 注解映射为 Netty GET");
    }

    @Test
    void emptyBaseUrlAndRouteYieldMethodPathOnly() {
        HelloController instance = new HelloController();
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("", instance, HelloController.class);
        assertEquals(1, result.size());
        assertEquals("/hello/1", result.get(0).url());
    }

    @Test
    void unannotatedMethodsAreSkipped() {
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("", new PlainController(), PlainController.class);
        assertTrue(result.isEmpty(), "未标注请求方式注解的方法不产出元信息");
    }

    public static class PlainController {

        public String nothing() {
            return "";
        }
    }

    @Test
    void allFiveVerbAnnotationsAreScanned() {
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("", new VerbController(), VerbController.class);
        assertEquals(5, result.size(), "五种请求方式注解都要被扫描");
        assertEquals(List.of("/d", "/g", "/h", "/p", "/u"), result.stream().map(RouteHandlerMetaInfo::url).toList(),
                "按 url 升序输出");
    }

    @Test
    void verbAnnotationsMapToNettyHttpMethods() {
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("", new VerbController(), VerbController.class);
        assertEquals(List.of(HttpMethod.DELETE, HttpMethod.GET, HttpMethod.PATCH, HttpMethod.POST, HttpMethod.PUT),
                result.stream().map(RouteHandlerMetaInfo::httpMethod).toList(),
                "五种请求方式注解分别映射为对应的 Netty HttpMethod");
    }

    @Test
    void multipleVerbAnnotationsOnOneMethodThrow() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RouteHandlerMetaInfoHelper.parse("", new MultiVerbController(), MultiVerbController.class));
        assertEquals("Route method hello declares multiple request method annotations", error.getMessage());
    }

    @Test
    void wildcardMethodPathIsPassedThroughToMetaInfo() {
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("", new WildcardController(), WildcardController.class);
        assertEquals(1, result.size());
        assertEquals("/bad*", result.get(0).url(), "通配符不属路径结构,合并不再拒绝,由路由语法校验方处理");
    }

    @Test
    void rootPathIsRejectedDuringMerge() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RouteHandlerMetaInfoHelper.parse("", new RootController(), RootController.class),
                "前缀、类路径、方法路径都提不出段,合并退化成根路径,不受支持");
        assertTrue(error.getMessage().contains("root path routing is not supported"), "异常由路径合并透出,文案归路径助手");
    }

    @Test
    void privateAnnotatedMethodIsIgnored() {
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("", new PrivateController(), PrivateController.class);
        assertEquals(1, result.size(), "私有方法即使标注也不解析");
        assertEquals("/pub", result.get(0).url());
    }

    public static class VerbController {

        @Get("/g")
        public String getHandler() {
            return "";
        }

        @Post("/p")
        public String postHandler() {
            return "";
        }

        @Put("/u")
        public String putHandler() {
            return "";
        }

        @Patch("/h")
        public String patchHandler() {
            return "";
        }

        @Delete("/d")
        public String deleteHandler() {
            return "";
        }
    }

    public static class MultiVerbController {

        @Get("/a")
        @Post("/b")
        public String hello() {
            return "";
        }
    }

    public static class WildcardController {

        @Get("/bad*")
        public String bad() {
            return "";
        }
    }

    public static class RootController {

        @Get
        public String defaultPath() {
            return "";
        }

        @Get("/")
        public String slashPath() {
            return "";
        }
    }

    public static class PrivateController {

        @Get("/pub")
        public String pub() {
            return "";
        }

        @Get("/priv")
        private String priv() {
            return "";
        }
    }

    @Test
    void nullRouteInstanceThrows() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> RouteHandlerMetaInfoHelper.parse("/x", null, VerbController.class));
        assertEquals("The route handler instance must not be null", error.getMessage());
        assertThrows(NullPointerException.class,
                () -> RouteHandlerMetaInfoHelper.parse(null, null, null), "实例判空先于类回落");
    }

    @Test
    void nullTargetClassFallsBackToInstanceClass() {
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("/base", new Child2(), null);
        assertEquals(1, result.size());
        assertSame(Child2.class, result.get(0).routeClass(), "缺省类参数取实例的类");
        assertEquals("/base/m", result.get(0).url(), "类级 Route 读自实例类 Child2(未标注,无继承)");
    }

    @Test
    void explicitTargetClassWinsOverInstanceClass() {
        Child2 instance = new Child2();
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("/base", instance, Parent2.class);
        assertEquals(1, result.size());
        assertSame(Parent2.class, result.get(0).routeClass(), "解析以显式 targetClass 为准,不用实例的类");
        assertEquals("/base/p2/m", result.get(0).url(), "类级 Route 读自 targetClass");
        assertSame(instance, result.get(0).instance());
    }

    @Test
    void nullBaseUrlTreatedAsEmpty() {
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse(null, new HelloController(), HelloController.class);
        assertEquals("/hello/1", result.get(0).url(), "baseUrl 为 null 按空串合并");
    }

    @Test
    void slashesAreNormalizedAcrossAllThreeSegments() {
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("api/", new NormalizeController(), NormalizeController.class);
        assertEquals(List.of("/api/c", "/api/c/sub/x"), result.stream().map(RouteHandlerMetaInfo::url).toList(),
                "三段各带缺前导或带尾斜杠,合并后以 / 开头且结尾不含 /");
    }

    @Test
    void consecutiveSlashInBaseUrlIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RouteHandlerMetaInfoHelper.parse("api//", new NormalizeController(), NormalizeController.class));
        assertTrue(error.getMessage().contains("api//"), "连续斜杠属非法路径,异常带原文直接透出");
    }

    @Test
    void consecutiveSlashInMethodAnnotationIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RouteHandlerMetaInfoHelper.parse("", new DoubleSlashController(), DoubleSlashController.class));
        assertTrue(error.getMessage().contains("//sub//x"), "方法级注解里的连续斜杠同样拒绝");
    }

    @Test
    void sameUrlMethodsOrderedByUrlThenNameThenParameters() {
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("", new SortController(), SortController.class);
        assertEquals(List.of("/a", "/a", "/b"), result.stream().map(RouteHandlerMetaInfo::url).toList());
        assertEquals(List.of("yMethod", "zMethod", "aMethod"),
                result.stream().map(info -> info.method().getName()).toList(), "同 url 按方法名升序");
    }

    @Test
    void overloadedSameUrlOrderedByParameterList() {
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("", new OverloadController(), OverloadController.class);
        assertEquals(2, result.size());
        assertEquals(0, result.get(0).method().getParameterCount(), "同名同 url 重载按参数表升序");
        assertEquals(1, result.get(1).method().getParameterCount());
    }

    @Test
    void helperIsAPrivateConstructorUtility() throws Exception {
        var constructor = RouteHandlerMetaInfoHelper.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()), "工具类构造器私有");
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance(), "构造器可为覆盖率测试实例化");
    }

    @Test
    void tripleVerbAnnotationCountAlsoRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RouteHandlerMetaInfoHelper.parse("", new TripleVerbController(), TripleVerbController.class));
        assertEquals("Route method triple declares multiple request method annotations", error.getMessage(), "三个注解同样落入多动词拒绝");
    }

    @Test
    void methodNameOrderDecidesBeforeParameterList() {
        List<RouteHandlerMetaInfo> result = RouteHandlerMetaInfoHelper.parse("",
                new ParamVsNameController(), ParamVsNameController.class);
        assertEquals(2, result.size());
        assertEquals("alpha", result.get(0).method().getName(), "同 url 先按方法名升序,参数表序仅作末位裁决");
        assertEquals("zed", result.get(1).method().getName());
    }

    public static class TripleVerbController {

        @Put("/t1")
        @Patch("/t2")
        @Delete("/t3")
        public String triple() {
            return "";
        }
    }

    public static class ParamVsNameController {

        @Get("/z")
        public String zed() {
            return "";
        }

        @Get("/z")
        public String alpha(String x) {
            return "";
        }
    }

    @Route("/p2")
    public static class Parent2 {

        @Get("m")
        public String m() {
            return "";
        }
    }

    public static class Child2 extends Parent2 {
    }

    @Route("/c/")
    public static class NormalizeController {

        @Get("sub/x/")
        public String sub() {
            return "";
        }

        @Get("")
        public String empty() {
            return "";
        }
    }

    public static class DoubleSlashController {

        @Get("//sub//x")
        public String sub() {
            return "";
        }
    }

    public static class SortController {

        @Get("/b")
        public String aMethod() {
            return "";
        }

        @Get("/a")
        public String zMethod() {
            return "";
        }

        @Get("/a")
        public String yMethod() {
            return "";
        }
    }

    public static class OverloadController {

        @Get("/dup")
        public String hello(String x) {
            return "";
        }

        @Get("/dup")
        public String hello() {
            return "";
        }
    }
}
