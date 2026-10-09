package top.turboweb.http.middleware.router;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.anno.method.Get;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.router.container.RouterContainer;
import top.turboweb.http.middleware.router.info.AutoBindRouterDefinition;
import top.turboweb.http.middleware.router.info.MethodRouterDefinition;
import top.turboweb.http.middleware.router.info.RouterDefinition;
import top.turboweb.http.testkit.fixtures.AutoBindProbeController;
import top.turboweb.http.testkit.fixtures.PrefixProbeController;
import top.turboweb.http.testkit.fixtures.RequestPathController;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnnoRouterManager} 的前缀规整、注册扫描与 autoBind 分派（spec §5：middleware/router）。
 *
 * <p>路由表通过同包可访问的 {@code getRouterContainer()} 直读，断言的是**精确集合**——
 * 夹具里多一个 `@Get` 方法、或少注册一条，都会让对应用例红。这是对 Review Focus 第 2 条
 * （"注解夹具会不会被扫描到"）的正面回答：`AnnoRouterManager` 无类路径扫描，
 * 只注册 `addController` 显式加入的实例，因此"扫到几个"完全由本用例自己决定。
 */
class AnnoRouterManagerTest {

    /** 通配符路径：`PathHelper.mergePath` 先于 AnnoRouterManager 自己的校验拒绝它。 */
    static class WildcardController {
        @Get("/bad/*")
        public String bad() {
            return "bad";
        }
    }

    /**
     * 问号路径：`PathHelper` 放行，落到 `doInitRouterDefinition` 的校验分支。
     *
     * <p>夹具必须是 public 且方法签名必须是单 `HttpContext` 参数：否则
     * `MethodRouterDefinition` 构造器会经由参数数量校验（:23）或 `unreflect` 的
     * IllegalAccessException 包装（:32-34）抛出同类型（`TurboRouterDefinitionCreateException`）
     * 异常，使类型断言无法把异常唯一归因到 `?` 校验分支（变异取证失效）。
     * 两处陷阱均有实验记录（task-09-mutation.log M17 段）。</p>
     */
    public static class QuestionPathController {
        @Get("/bad/?x")
        public String bad(HttpContext ctx) {
            return "bad";
        }
    }

    private static Map<String, RouterDefinition> exact(RouterContainer container, String method) {
        return container.getExactRouterInfo().getRouterDefinition(method);
    }

    private static HttpContext ctx(HttpMethod method, String uri) {
        return new FullHttpContext(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri), null, null, null, null);
    }

    private static AnnoRouterManager initialized(String prefix, Object controller) {
        AnnoRouterManager manager = new AnnoRouterManager(prefix);
        manager.addController(controller);
        manager.init(null);
        return manager;
    }

    @Test
    void emptyPrefixRegistersRootRelativePath() {
        AnnoRouterManager manager = initialized("", new PrefixProbeController());

        assertEquals(Set.of("/ping"), exact(manager.getRouterContainer(), "GET").keySet());
    }

    @Test
    void slashPrefixBehavesLikeEmptyPrefix() {
        AnnoRouterManager manager = initialized("/", new PrefixProbeController());

        assertEquals(Set.of("/ping"), exact(manager.getRouterContainer(), "GET").keySet());
    }

    @Test
    void nullPrefixBehavesLikeEmptyPrefix() {
        AnnoRouterManager manager = initialized(null, new PrefixProbeController());

        assertEquals(Set.of("/ping"), exact(manager.getRouterContainer(), "GET").keySet());
    }

    @Test
    void trailingSlashPrefixIsStripped() {
        AnnoRouterManager manager = initialized("/api/", new PrefixProbeController());

        assertEquals(Set.of("/api/ping"), exact(manager.getRouterContainer(), "GET").keySet());
    }

    @Test
    @DisplayName("HS-9 回归：缺前导斜杠的前缀规整补上前导斜杠")
    void prefixWithoutLeadingSlashIsNormalized() {
        AnnoRouterManager manager = initialized("api", new PrefixProbeController());

        assertEquals(Set.of("/api/ping"), exact(manager.getRouterContainer(), "GET").keySet());
    }

    @Test
    void initRegistersExactlyTheAnnotatedRoutesOfTheAddedController() {
        AnnoRouterManager manager = initialized("", new RequestPathController());
        RouterContainer container = manager.getRouterContainer();

        assertEquals(Set.of("/fixture/ping", "/fixture/user/{id}"),
                exact(container, "GET").keySet(),
                "无注解方法（如 Object 继承来的）与未加入的控制器都不得出现在路由表里");
        assertEquals(Set.of("/fixture/echo"), exact(container, "POST").keySet());
        assertTrue(exact(container, "PUT").isEmpty());
    }

    @Test
    void autoBindFalseProducesMethodRouterDefinition() {
        AnnoRouterManager manager = initialized("", new RequestPathController());

        assertInstanceOf(MethodRouterDefinition.class,
                exact(manager.getRouterContainer(), "GET").get("/fixture/ping"));
    }

    @Test
    void autoBindTrueProducesAutoBindRouterDefinition() {
        AnnoRouterManager manager = new AnnoRouterManager(true);
        manager.addController(new AutoBindProbeController());
        manager.init(null);

        assertInstanceOf(AutoBindRouterDefinition.class,
                exact(manager.getRouterContainer(), "GET").get("/auto"));
    }

    @Test
    @DisplayName("钉住 HS-11：含参路径同时落入 trie 与精确表")
    void pathVariableRouteIsRegisteredInBothTrieAndExact() {
        AnnoRouterManager manager = initialized("", new RequestPathController());
        RouterContainer container = manager.getRouterContainer();

        // 钉住现状：若将来修正，本用例应改为期望正确行为（含参路径只进 trie）
        assertNotNull(container.exactMatch("GET", "/fixture/user/{id}"),
                "注解路由把含参路径同时放进精确表");
        RouterContainer.TrieMatchResult match = container.trieMatch("GET", "/fixture/user/42");
        assertNotNull(match);
        assertEquals("42", match.params().get("id"));
    }

    @Test
    void wildcardPathIsRejectedBeforeDefinitionCreation() {
        AnnoRouterManager manager = new AnnoRouterManager();
        manager.addController(new WildcardController());

        assertThrows(IllegalArgumentException.class, () -> manager.init(null),
                "PathHelper.mergePath 先抛 IAE，AnnoRouterManager 自己的通配符校验是死分支");
    }

    @Test
    void questionMarkPathThrowsRouterDefinitionCreateException() {
        AnnoRouterManager manager = new AnnoRouterManager();
        manager.addController(new QuestionPathController());

        assertThrows(TurboRouterDefinitionCreateException.class, () -> manager.init(null));
    }

    @Test
    void initIsIdempotent() {
        AnnoRouterManager manager = new AnnoRouterManager();
        manager.addController(new RequestPathController());
        manager.init(null);
        Set<String> before = Set.copyOf(exact(manager.getRouterContainer(), "GET").keySet());

        manager.init(null);

        assertEquals(before, exact(manager.getRouterContainer(), "GET").keySet(),
                "第二次 init 必须被 isInit 挡住；若重复注册，ExactRouterInfo 会抛 ROUTER_REPEAT");
    }

    @Test
    void addControllerRejectsNullController() {
        AnnoRouterManager manager = new AnnoRouterManager();

        assertThrows(NullPointerException.class, () -> manager.addController(null));
    }

    @Test
    void addControllerRejectsNullOriginClass() {
        AnnoRouterManager manager = new AnnoRouterManager();

        assertThrows(NullPointerException.class,
                () -> manager.addController(new PrefixProbeController(), null));
    }

    @Test
    void invokeDispatchesToControllerMethod() {
        AnnoRouterManager manager = initialized("", new RequestPathController());

        assertEquals("pong", manager.invoke(ctx(HttpMethod.GET, "/fixture/ping")));
    }

    @Test
    void autoBindManagerInvokesZeroArgumentRoute() {
        AnnoRouterManager manager = new AnnoRouterManager(true);
        manager.addController(new AutoBindProbeController());
        manager.init(null);

        assertEquals("auto", manager.invoke(ctx(HttpMethod.GET, "/auto")));
    }
}
