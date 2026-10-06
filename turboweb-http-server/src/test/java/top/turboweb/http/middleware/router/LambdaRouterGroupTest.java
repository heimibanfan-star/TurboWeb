package top.turboweb.http.middleware.router;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.router.container.RouterContainer;
import top.turboweb.http.middleware.router.info.RouterDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LambdaRouterGroup} 的注册器语义与 {@link LambdaRouterManager#addGroup} 的分派
 * （spec §5：middleware/router）。
 *
 * <p>lambda 路由不走注解：含 `{` 的路径只进 trie，其余只进精确表——这两条分支各有一例，
 * 用来钉住"加了参数名却仍查精确表就查不到"这一正确行为。
 */
class LambdaRouterGroupTest {

    private static final LambdaHandler HANDLER = context -> "lambda-result";

    private static LambdaRouterGroup group(String prefix,
                                           Consumer<LambdaRouterGroup.RouterRegister> body) {
        return new LambdaRouterGroup() {
            @Override
            public String requestPath() {
                return prefix;
            }

            @Override
            protected void registerRoute(LambdaRouterGroup.RouterRegister register) {
                body.accept(register);
            }
        };
    }

    private static HttpContext ctx(HttpMethod method, String uri) {
        return new FullHttpContext(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri), null, null, null, null);
    }

    @Test
    void routerRegisterRecordsExplicitPathForEveryHttpMethod() {
        LambdaRouterGroup group = group("", register -> {
            register.get("/g", HANDLER);
            register.post("/p", HANDLER);
            register.put("/pu", HANDLER);
            register.patch("/pa", HANDLER);
            register.delete("/d", HANDLER);
        });

        List<LambdaRouterGroup.RouterInfo> routers = group.getRouters();

        assertEquals(5, routers.size());
        assertEquals("GET", routers.get(0).getMethod());
        assertEquals("/g", routers.get(0).getPath());
        assertEquals("POST", routers.get(1).getMethod());
        assertEquals("/p", routers.get(1).getPath());
        assertEquals("PUT", routers.get(2).getMethod());
        assertEquals("/pu", routers.get(2).getPath());
        assertEquals("PATCH", routers.get(3).getMethod());
        assertEquals("/pa", routers.get(3).getPath());
        assertEquals("DELETE", routers.get(4).getMethod());
        assertEquals("/d", routers.get(4).getPath());
    }

    @Test
    void routerRegisterDefaultsPathToRootForEveryHttpMethod() {
        LambdaRouterGroup group = group("", register -> {
            register.get(HANDLER);
            register.post(HANDLER);
            register.put(HANDLER);
            register.patch(HANDLER);
            register.delete(HANDLER);
        });

        List<LambdaRouterGroup.RouterInfo> routers = group.getRouters();

        assertEquals(5, routers.size());
        for (LambdaRouterGroup.RouterInfo router : routers) {
            assertEquals("/", router.getPath(),
                    "无路径重载应落到根路径，实际: " + router.getMethod() + " -> " + router.getPath());
        }
    }

    @Test
    void routerRegisterChainsAndAccumulates() {
        AtomicReference<LambdaRouterGroup.RouterRegister> seen = new AtomicReference<>();
        LambdaRouterGroup group = group("", register -> {
            seen.set(register);
            assertSame(register, register.get("/a", HANDLER));
            register.get("/b", HANDLER);
        });

        assertEquals(2, group.getRouters().size());
        assertNotNull(seen.get());
    }

    @Test
    void getRoutersRunsRegisterRouteLazily() {
        List<String> calls = new ArrayList<>();
        LambdaRouterGroup group = new LambdaRouterGroup() {
            @Override
            public String requestPath() {
                return "";
            }

            @Override
            protected void registerRoute(LambdaRouterGroup.RouterRegister register) {
                calls.add("register");
                register.get("/a", HANDLER);
            }
        };

        assertTrue(calls.isEmpty(), "构造阶段不得注册任何路由");
        assertEquals(1, group.getRouters().size());
        assertEquals(1, calls.size());
    }

    @Test
    @DisplayName("钉住 HS-10：getRouters() 非幂等，重复调用会重复追加路由")
    void getRoutersIsNotIdempotent() {
        List<String> calls = new ArrayList<>();
        LambdaRouterGroup group = new LambdaRouterGroup() {
            @Override
            public String requestPath() {
                return "";
            }

            @Override
            protected void registerRoute(LambdaRouterGroup.RouterRegister register) {
                calls.add("register");
                register.get("/a", HANDLER);
            }
        };

        assertEquals(1, group.getRouters().size());
        // 期望的正确行为是缓存一份路由列表并原样返回；当前每次调用都重新跑 registerRoute
        // 往同一个 ArrayList 追加。把同一个组注册进两个管理器会因此重复注册 → ROUTER_REPEAT。
        assertEquals(2, group.getRouters().size());
        assertEquals(2, calls.size());
    }

    @Test
    void requestPathDefaultsToEmptyString() {
        LambdaRouterGroup group = new LambdaRouterGroup() {
            @Override
            protected void registerRoute(LambdaRouterGroup.RouterRegister register) {
            }
        };

        assertEquals("", group.requestPath());
    }

    @Test
    void addGroupRegistersExactRouteAndInvokesLambda() throws Throwable {
        LambdaRouterManager manager = new LambdaRouterManager();

        assertSame(manager, manager.addGroup(group("", register -> register.get("/x", HANDLER))));

        RouterDefinition definition = manager.getRouterContainer().exactMatch("GET", "/x");
        assertNotNull(definition);
        assertEquals("lambda-result", definition.invoke(ctx(HttpMethod.GET, "/x")));
    }

    @Test
    void addGroupRegistersPathVariableRouteOnlyInTrie() {
        LambdaRouterManager manager = new LambdaRouterManager();
        manager.addGroup(group("", register -> register.get("/user/{id}", HANDLER)));

        RouterContainer container = manager.getRouterContainer();
        assertNull(container.exactMatch("GET", "/user/{id}"),
                "含参路径不得进精确表——查精确表拿到的定义不会注入路径参数");
        RouterContainer.TrieMatchResult match = container.trieMatch("GET", "/user/7");
        assertNotNull(match);
        assertEquals(Set.of("id"), match.params().keySet());
        assertEquals("7", match.params().get("id"));
    }

    @Test
    void addGroupAppliesGroupPrefix() {
        LambdaRouterManager manager = new LambdaRouterManager();
        manager.addGroup(group("/api", register -> register.get("/x", HANDLER)));

        assertNotNull(manager.getRouterContainer().exactMatch("GET", "/api/x"));
    }

    @Test
    void addGroupRejectsWildcardPath() {
        LambdaRouterManager manager = new LambdaRouterManager();

        assertThrows(IllegalArgumentException.class,
                () -> manager.addGroup(group("", register -> register.get("/bad/*", HANDLER))),
                "PathHelper.mergePath 先拒掉 *，LambdaRouterManager:31-33 的检查是死分支");
    }
}
