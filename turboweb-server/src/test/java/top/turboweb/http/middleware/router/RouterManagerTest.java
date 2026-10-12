package top.turboweb.http.middleware.router;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.exception.TurboRequestException;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.router.container.DefaultRouterContainer;
import top.turboweb.http.middleware.router.container.RouterContainer;
import top.turboweb.http.middleware.router.info.AutoBindRouterDefinition;
import top.turboweb.http.middleware.router.info.MethodRouterDefinition;
import top.turboweb.http.middleware.router.info.RouterDefinition;
import top.turboweb.http.testkit.RecordingMiddleware;
import top.turboweb.http.testkit.fixtures.PrefixProbeController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link RouterManager} 的分发骨架与两种 {@link RouterDefinition} 的契约（spec §5：middleware/router）。
 *
 * <p>同包测试，`getRouterContainer()` 的 `protected` 覆盖可直接调用，因此可以用真实
 * {@code DefaultRouterContainer} 摆出路由表，再喂内存版 ctx 断言命中结果——全部是确定量，
 * 无端口、无线程、无 sleep。
 */
class RouterManagerTest {

    /** 夹具：持真实容器、暴露 `protected` 容器供测试摆路由。 */
    private static final class TestRouterManager extends RouterManager {
        final RouterContainer container = new DefaultRouterContainer();

        @Override
        protected RouterContainer getRouterContainer() {
            return container;
        }
    }

    private static final RouterDefinition RETURNS_HANDLED = ctx -> "handled";

    private static final RouterDefinition THROWS_CHECKED = ctx -> {
        throw new IOException("checked-boom");
    };

    private static final RouterDefinition THROWS_RUNTIME = ctx -> {
        throw new IllegalStateException("runtime-boom");
    };

    private static HttpContext ctx(HttpMethod method, String uri) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
        return new FullHttpContext(request, null, null, null, null);
    }

    @Test
    void initLeavesNextUnchangedWhenNoChainWasSet() {
        TestRouterManager manager = new TestRouterManager();
        assertNull(manager.getNext());
        manager.init(null);
        assertNull(manager.getNext(), "RouterManager 不接受后继中间件，init 不得改动链");
    }

    @Test
    void initLeavesExistingNextInPlace() {
        TestRouterManager manager = new TestRouterManager();
        RecordingMiddleware alreadySet = new RecordingMiddleware("already", new ArrayList<>());
        manager.setNext(alreadySet);

        manager.init(new RecordingMiddleware("chain", new ArrayList<>()));

        assertSame(alreadySet, manager.getNext(),
                "RouterManager.init 只告警、不摘链；若改成 setNext(null) 本用例应红");
    }

    @Test
    void invokeThrowsRouterNotMatchWhenNothingMatches() {
        TestRouterManager manager = new TestRouterManager();

        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> manager.invoke(ctx(HttpMethod.GET, "/nope")));

        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, ex.getCode());
        assertNotNull(ex.getMessage());
    }

    @Test
    void invokeRejectsEmptyUri() {
        TestRouterManager manager = new TestRouterManager();

        assertThrows(TurboRequestException.class, () -> manager.invoke(ctx(HttpMethod.GET, "")));
    }

    @Test
    void invokeFindsExactRouteFirst() {
        TestRouterManager manager = new TestRouterManager();
        manager.container.getExactRouterInfo().addRouter("GET", "/hit", RETURNS_HANDLED);

        HttpContext ctx = ctx(HttpMethod.GET, "/hit");

        assertEquals("handled", manager.invoke(ctx));
        assertNull(ctx.param("id"), "精确命中不注入任何路径参数");
    }

    @Test
    void invokeFallsBackToTrieRouteAndInjectsPathParams() {
        TestRouterManager manager = new TestRouterManager();
        manager.container.getTrieRouterInfo().addRouter("GET", "/user/{id}", RETURNS_HANDLED);

        HttpContext ctx = ctx(HttpMethod.GET, "/user/42");

        assertEquals("handled", manager.invoke(ctx));
        assertEquals("42", ctx.param("id"), "trie 命中必须把路径参数注入上下文");
    }

    @Test
    void invokeWrapsCheckedThrowableAsRouterInvokeError() {
        TestRouterManager manager = new TestRouterManager();
        manager.container.getExactRouterInfo().addRouter("GET", "/boom", THROWS_CHECKED);

        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> manager.invoke(ctx(HttpMethod.GET, "/boom")));

        assertEquals(RouteHandlerException.ROUTER_INVOKE_ERROR, ex.getCode());
        assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    void invokePropagatesRuntimeExceptionUnwrapped() {
        TestRouterManager manager = new TestRouterManager();
        manager.container.getExactRouterInfo().addRouter("GET", "/boom", THROWS_RUNTIME);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> manager.invoke(ctx(HttpMethod.GET, "/boom")));

        assertEquals("runtime-boom", ex.getMessage());
    }

    @Test
    void methodRouterDefinitionRejectsMethodWithWrongArity() throws Exception {
        Method twoParams = PrefixProbeController.class.getMethod(
                "two", HttpContext.class, String.class);

        assertThrows(TurboRouterDefinitionCreateException.class,
                () -> new MethodRouterDefinition(new PrefixProbeController(), twoParams));
    }

    @Test
    void methodRouterDefinitionRejectsNonHttpContextParameter() throws Exception {
        Method oneString = PrefixProbeController.class.getMethod("one", String.class);

        assertThrows(TurboRouterDefinitionCreateException.class,
                () -> new MethodRouterDefinition(new PrefixProbeController(), oneString));
    }

    @Test
    void methodRouterDefinitionInvokesBoundTarget() throws Throwable {
        PrefixProbeController target = new PrefixProbeController();
        Method ping = PrefixProbeController.class.getMethod("ping", HttpContext.class);
        MethodRouterDefinition definition = new MethodRouterDefinition(target, ping);

        assertEquals("pong", definition.invoke(ctx(HttpMethod.GET, "/ping")));
    }

    @Test
    void autoBindDefinitionFallsBackToDefaultBinderWhenNoParserMatches() throws Throwable {
        PrefixProbeController target = new PrefixProbeController();
        Method one = PrefixProbeController.class.getMethod("one", String.class);
        AutoBindRouterDefinition definition =
                new AutoBindRouterDefinition(List.of(), target, one);

        Object result = definition.invoke(ctx(HttpMethod.GET, "/one"));

        assertEquals("null-arg", result,
                "空 parser 列表下参数必须落 DEFAULT_PARAMETER_BINDER（返回 null），而不是抛异常");
    }

    @Test
    void autoBindDefinitionSupportsZeroArgumentMethod() throws Throwable {
        PrefixProbeController target = new PrefixProbeController();
        Method zero = PrefixProbeController.class.getMethod("zero");
        AutoBindRouterDefinition definition =
                new AutoBindRouterDefinition(List.of(), target, zero);

        assertEquals("zero", definition.invoke(ctx(HttpMethod.GET, "/zero")));
    }
}
